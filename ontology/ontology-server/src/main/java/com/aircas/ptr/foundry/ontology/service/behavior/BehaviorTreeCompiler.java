package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorRunModeEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorGroupConfig;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeDefinition;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyActionNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyConditionNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyCooldownNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyForceSuccessNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyGuardNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyParallelNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyRepeaterNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyTimeLimitNode;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyUntilNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.config.BTTreeCfg;
import com.github.silencesu.behavior3java.core.BaseNode;
import com.github.silencesu.behavior3java.core.BehaviorTree;
import lombok.SneakyThrows;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 把库里的树定义编译成 behavior3java 可执行的树。
 * <p>定义模型是业务侧的嵌套结构，库要的是「节点表 + 根 id」。转换时做两件事：
 * 每个定义节点外面包一层 Guard（统一处理 enabled/skipIf 并记录逐节点结果），
 * 其余按 kind 落到库内置容器或自定义节点上。节点配置以 JSON 字符串放进 properties，
 * 因为 behavior3java 的 properties 只能是字符串。</p>
 */
public final class BehaviorTreeCompiler {

    /** 每个定义节点外层的包装节点名，注册到自定义节点表里 */
    public static final String GUARD_NODE = "OntologyGuard";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, Class<? extends BaseNode>> CUSTOM_NODES = Map.of(
            GUARD_NODE, OntologyGuardNode.class,
            "OntologyParallel", OntologyParallelNode.class,
            "OntologyRepeater", OntologyRepeaterNode.class,
            "OntologyUntil", OntologyUntilNode.class,
            "OntologyCooldown", OntologyCooldownNode.class,
            "OntologyTimeLimit", OntologyTimeLimitNode.class,
            "OntologyForceSuccess", OntologyForceSuccessNode.class,
            "OntologyAction", OntologyActionNode.class,
            "OntologyCondition", OntologyConditionNode.class);

    private BehaviorTreeCompiler() {
    }

    public static BehaviorTree compile(String api, BehaviorTreeDefinition definition) {
        var roots = definition.getRoots();
        if (roots.size() != 1) {
            throw new BusinessException("行为树定义不完整，根节点数量不是 1", HttpStatus.BAD_REQUEST);
        }
        var cfg = new BTTreeCfg();
        cfg.setId(api);
        cfg.setTitle(api);
        Map<String, BTNodeCfg> nodes = new LinkedHashMap<>();
        var sequence = new AtomicInteger();
        var rootKey = compileNode(roots.get(0), null, nodes, sequence);
        cfg.setRoot(rootKey);
        cfg.setNodes(nodes);
        var tree = new BehaviorTree();
        tree.load(cfg, CUSTOM_NODES);
        return tree;
    }

    /**
     * 编译一个定义节点，返回它在节点表里的 key（Guard 的 key，父节点连的是它）。
     *
     * @param parentKind 父定义节点的类型；Guard 据此决定「跳过」返回成功还是失败，根节点为空
     */
    private static String compileNode(BehaviorTreeNode node, BehaviorNodeKindEnum parentKind,
                                      Map<String, BTNodeCfg> nodes, AtomicInteger sequence) {
        if (node.getKind() == null) {
            throw new BusinessException("行为树节点缺少类型：" + node.getId(), HttpStatus.BAD_REQUEST);
        }
        var bodyKey = "n" + sequence.incrementAndGet();
        var guardKey = "g" + sequence.incrementAndGet();

        var body = new BTNodeCfg();
        body.setId(bodyKey);
        body.setName(bodyNodeName(node));
        body.setCategory(bodyCategory(node.getKind()));
        body.setTitle(node.getDisplayName());
        body.setProperties(bodyProperties(node));
        if (node.getKind().getCategory() == BehaviorNodeCategoryEnum.COMPOSITE) {
            List<String> children = new ArrayList<>();
            for (var child : node.getChildren()) {
                children.add(compileNode(child, node.getKind(), nodes, sequence));
            }
            body.setChildren(children);
        } else if (node.getKind().getCategory() == BehaviorNodeCategoryEnum.DECORATOR) {
            body.setChild(compileNode(node.getChildren().get(0), node.getKind(), nodes, sequence));
        }
        nodes.put(bodyKey, body);

        var guard = new BTNodeCfg();
        guard.setId(guardKey);
        guard.setName(GUARD_NODE);
        guard.setCategory("decorator");
        guard.setChild(bodyKey);
        Map<String, String> guardProps = new HashMap<>();
        guardProps.put("nodeId", node.getId());
        guardProps.put("enabled", String.valueOf(node.isEnabled()));
        if (parentKind != null) {
            guardProps.put("parentKind", parentKind.name());
        }
        if (node.getSkipIf() != null) {
            guardProps.put("skipIf", node.getSkipIf());
        }
        // 循环节点要在每轮重新求值，所以 body 也带一份；Guard 只做首次进入前的判断
        if (StringUtils.isNotBlank(node.getWhileCondition())) {
            guardProps.put("whileCondition", node.getWhileCondition());
        }
        guard.setProperties(guardProps);
        nodes.put(guardKey, guard);
        return guardKey;
    }

    /**
     * 容器节点按执行模式选库内置实现：记忆模式记住上次成功位置，标准/响应式从第一个子节点重来。
     */
    private static String bodyNodeName(BehaviorTreeNode node) {
        return switch (node.getKind()) {
            case SEQUENCE -> isMemory(node) ? "MemSequence" : "Sequence";
            case SELECTOR -> isMemory(node) ? "MemPriority" : "Priority";
            case PARALLEL -> "OntologyParallel";
            case INVERTER -> "Inverter";
            case REPEATER -> "OntologyRepeater";
            case UNTIL_SUCCESS, UNTIL_FAILURE -> "OntologyUntil";
            case COOLDOWN -> "OntologyCooldown";
            case TIME_LIMIT -> "OntologyTimeLimit";
            case FORCE_SUCCESS -> "OntologyForceSuccess";
            case BLACKBOARD_CONDITION, CONDITION -> "OntologyCondition";
            case ACTION -> "OntologyAction";
        };
    }

    private static String bodyCategory(BehaviorNodeKindEnum kind) {
        return switch (kind.getCategory()) {
            case COMPOSITE -> "composite";
            case DECORATOR -> "decorator";
            case LEAF -> kind == BehaviorNodeKindEnum.ACTION ? "action" : "condition";
        };
    }

    private static Map<String, String> bodyProperties(BehaviorTreeNode node) {
        Map<String, String> props = new HashMap<>();
        props.put("nodeId", node.getId());
        props.put("kind", node.getKind().name());
        if (node.getConfig() != null) {
            props.put("configJson", writeJson(node.getConfig()));
        }
        if (StringUtils.isNotBlank(node.getWhileCondition())) {
            props.put("whileCondition", node.getWhileCondition());
        }
        // 并行节点要按分支定位 skipped 标记，把子节点标识按顺序带过去
        if (node.getKind() == BehaviorNodeKindEnum.PARALLEL) {
            props.put("childNodeIds", writeJson(node.getChildren().stream().map(BehaviorTreeNode::getId).toList()));
        }
        return props;
    }

    private static boolean isMemory(BehaviorTreeNode node) {
        if (node.getConfig() == null) {
            return false;
        }
        var config = JSON.convertValue(node.getConfig(), BehaviorGroupConfig.class);
        return config != null && config.getRunMode() == BehaviorRunModeEnum.MEMORY;
    }

    @SneakyThrows
    private static String writeJson(Object value) {
        return JSON.writeValueAsString(value);
    }
}
