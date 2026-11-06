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
     * 取一棵可执行的树：校验存在、是树、已发布，未命中缓存则从库里的 definition 编译。
     * <p>行为 api 只在空间内唯一，树也按空间定位，避免跨空间同名树互相覆盖缓存或取错定义。</p>
     */
    public CompiledBehaviorTree getTree(Integer ontologySpaceId, String api) {
        var cacheKey = cacheKey(ontologySpaceId, api);
        var cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        var compiled = load(ontologySpaceId, api);
        cache.put(cacheKey, compiled);
        return compiled;
    }

    public void invalidate(Integer ontologySpaceId, String api) {
        if (ontologySpaceId != null && StringUtils.isNotBlank(api)) {
            cache.invalidate(cacheKey(ontologySpaceId, api));
        }
    }

    private String cacheKey(Integer ontologySpaceId, String api) {
        return ontologySpaceId + ":" + api;
    }

    private CompiledBehaviorTree load(Integer ontologySpaceId, String api) {
        var action = ontologyActionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, api)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(action.getActionKind() == ActionKindEnum.TREE,
                "按行为执行的接口不能跑行为树：" + api, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(action.getStatus(), ActionStatusEnum.PUBLISHED.getValue()),
                "行为树未发布，不能执行：" + api, HttpStatus.BAD_REQUEST);
        var definition = readDefinition(action.getDefinition(), api);
        var tree = BehaviorTreeCompiler.compile(api, definition);
        return new CompiledBehaviorTree(action.getApi(), action.getOntologySpaceId(), action.getStatus(), definition, tree);
    }

    @SneakyThrows
    private BehaviorTreeDefinition readDefinition(String definition, String api) {
        if (StringUtils.isBlank(definition)) {
            throw new BusinessException("行为树定义为空：" + api, HttpStatus.BAD_REQUEST);
        }
        return jsonMapper.readValue(definition, BehaviorTreeDefinition.class);
    }
}
