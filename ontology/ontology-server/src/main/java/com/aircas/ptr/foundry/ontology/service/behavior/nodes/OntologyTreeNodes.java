package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.service.behavior.BehaviorTreeRuntime;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Tick;
import lombok.SneakyThrows;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 自定义节点共用的取值与状态转换。
 * <p>behavior3java 的 BTNodeCfg.properties 只能是字符串，节点配置在这里以 JSON 字符串传递。</p>
 */
public final class OntologyTreeNodes {

    /**
     * 单次请求内的循环兜底上限。次数配得过大、条件永远成立时，不能让一次执行无限跑下去；
     * 固定次数超过上限会在节点说明里提示，条件循环达到上限按失败上报。
     */
    public static final int MAX_LOOP_ITERATIONS = 1000;

    /**
     * 单个并行节点一次执行的并发上限。分支里的行为会真实落库，并发过大等于瞬时占满数据库连接池，
     * 配置超过上限时按上限执行并在节点说明里写明。
     */
    public static final int MAX_PARALLEL_CONCURRENCY = 8;

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

    /** 编译器写进节点的节点标识列表，并行节点用它按分支定位执行痕迹 */
    @SneakyThrows
    public static List<String> stringList(Map<String, String> props, String key) {
        var json = props.get(key);
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        List<String> values = JSON.readValue(json, new TypeReference<>() {
        });
        return values == null ? new ArrayList<>() : values;
    }

    /**
     * 单轮引擎不会恢复 RUNNING：配置里写好但历史存下来的 RUNNING 按失败上报，
     * 避免一次执行的结果停在「还没跑完」这种不可恢复的状态。
     */
    public static BehaviorStateEnum singleTickState(BehaviorStateEnum state) {
        return state == null || state == BehaviorStateEnum.RUNNING ? BehaviorStateEnum.FAILURE : state;
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
