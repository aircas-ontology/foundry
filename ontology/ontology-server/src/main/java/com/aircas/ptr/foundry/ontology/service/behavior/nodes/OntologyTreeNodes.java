package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.service.behavior.BehaviorTreeRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Tick;
import lombok.SneakyThrows;

import java.util.Collections;
import java.util.Map;

/**
 * 自定义节点共用的取值与状态转换。
 * <p>behavior3java 的 BTNodeCfg.properties 只能是字符串，节点配置在这里以 JSON 字符串传递。</p>
 */
public final class OntologyTreeNodes {

    private static final ObjectMapper JSON = new ObjectMapper();

    private OntologyTreeNodes() {
    }

    public static Map<String, String> props(BTNodeCfg cfg) {
        return cfg.getProperties() == null ? Collections.emptyMap() : cfg.getProperties();
    }

    @SneakyThrows
    public static <T> T config(Map<String, String> props, Class<T> configType) {
        var json = props.get("configJson");
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.readValue(json, configType);
    }

    /** 节点只能通过 Tick 拿上下文，目标对象在服务侧固定塞成运行时 */
    public static BehaviorTreeRuntime runtime(Tick tick) {
        if (tick.getTarget() instanceof BehaviorTreeRuntime runtime) {
            return runtime;
        }
        throw new IllegalStateException("行为树执行缺少运行时上下文");
    }

    public static B3Status toB3(BehaviorStateEnum state) {
        return switch (state) {
            case SUCCESS -> B3Status.SUCCESS;
            case FAILURE -> B3Status.FAILURE;
            case RUNNING -> B3Status.RUNNING;
        };
    }

    /** ERROR 不是 BT 的标准返回值，按失败处理，避免整棵树报一个前端不认识的状态 */
    public static BehaviorStateEnum toState(B3Status status) {
        return switch (status) {
            case SUCCESS -> BehaviorStateEnum.SUCCESS;
            case RUNNING -> BehaviorStateEnum.RUNNING;
            default -> BehaviorStateEnum.FAILURE;
        };
    }
}
