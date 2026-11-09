package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionStatusEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeDefinition;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * 行为树编译结果缓存。
 * <p>树没有版本，definition 一改就整棵重编译；按「空间 + api」缓存，保存、发布、删除时失效。
 * behavior3java 的节点不保存执行状态（isOpen、记忆位置都在 Blackboard 与 Tick 上），
 * 编译结果可以被多次执行复用。</p>
 */
@Slf4j
@Component
public class BtTreeManager {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final Cache<String, CompiledBehaviorTree> cache = Caffeine.newBuilder()
            .maximumSize(512)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    /**
     * 取一棵可执行的树：每次命中缓存都回库核对状态与更新时间，未命中缓存则从库里的 definition 编译。
     * <p>失效只在当前实例生效，其他实例的缓存看不到本地保存/发布；不核对的话，多实例部署下会继续
     * 用旧的已发布定义或已下线的树执行。行为 api 只在空间内唯一，树也按空间定位。</p>
     */
    public CompiledBehaviorTree getTree(Integer ontologySpaceId, String api) {
        var cacheKey = cacheKey(ontologySpaceId, api);
        var cached = cache.getIfPresent(cacheKey);
        if (cached != null && isFresh(ontologySpaceId, api, cached)) {
            return cached;
        }
        var compiled = load(ontologySpaceId, api);
        cache.put(cacheKey, compiled);
        return compiled;
    }

    /**
     * 试跑用：草稿树也能编译，且不进执行缓存，避免草稿定义覆盖已发布树。
     * <p>调用方负责按保存期规则再校验一次定义，这里只保证存在且是树。</p>
     */
    public CompiledBehaviorTree compileForTest(Integer ontologySpaceId, String api) {
        var action = findAction(ontologySpaceId, api);
        PreconditionUtils.checkArgument(action.getActionKind() == ActionKindEnum.TREE,
                "行为树测试只能传行为树 api：" + api, HttpStatus.BAD_REQUEST);
        return compile(action);
    }

    public void invalidate(Integer ontologySpaceId, String api) {
        if (ontologySpaceId != null && StringUtils.isNotBlank(api)) {
            cache.invalidate(cacheKey(ontologySpaceId, api));
        }
    }

    /** 删对象会连带删掉该空间下的树与行为，整空间的编译结果一起失效，避免残留缓存继续命中 */
    public void invalidateSpace(Integer ontologySpaceId) {
        if (ontologySpaceId == null) {
            return;
        }
        var prefix = ontologySpaceId + ":";
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String cacheKey(Integer ontologySpaceId, String api) {
        return ontologySpaceId + ":" + api;
    }

    /**
     * 缓存是否仍与库里的行为一致：状态还是已发布，且定义没有在别的实例被改过。
     * 只查两列，执行前多一次轻量查询，换来的是一致性。
     */
    private boolean isFresh(Integer ontologySpaceId, String api, CompiledBehaviorTree cached) {
        var latest = ontologyActionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .select(OntologyAction::getStatus, OntologyAction::getUpdateTime)
                .eq(OntologyAction::getApi, api)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        return latest != null
                && Objects.equals(latest.getStatus(), ActionStatusEnum.PUBLISHED.getValue())
                && Objects.equals(latest.getUpdateTime(), cached.updateTime());
    }

    private CompiledBehaviorTree load(Integer ontologySpaceId, String api) {
        var action = findAction(ontologySpaceId, api);
        PreconditionUtils.checkArgument(action.getActionKind() == ActionKindEnum.TREE,
                "按行为执行的接口不能跑行为树：" + api, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(action.getStatus(), ActionStatusEnum.PUBLISHED.getValue()),
                "行为树未发布，不能执行：" + api, HttpStatus.BAD_REQUEST);
        return compile(action);
    }

    private OntologyAction findAction(Integer ontologySpaceId, String api) {
        var action = ontologyActionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, api)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        return action;
    }

    private CompiledBehaviorTree compile(OntologyAction action) {
        var definition = readDefinition(action.getDefinition(), action.getApi());
        var tree = BehaviorTreeCompiler.compile(action.getApi(), definition);
        return new CompiledBehaviorTree(action.getApi(), action.getOntologySpaceId(), action.getStatus(),
                action.getUpdateTime(), definition, tree);
    }

    @SneakyThrows
    private BehaviorTreeDefinition readDefinition(String definition, String api) {
        if (StringUtils.isBlank(definition)) {
            throw new BusinessException("行为树定义为空：" + api, HttpStatus.BAD_REQUEST);
        }
        return jsonMapper.readValue(definition, BehaviorTreeDefinition.class);
    }
}
