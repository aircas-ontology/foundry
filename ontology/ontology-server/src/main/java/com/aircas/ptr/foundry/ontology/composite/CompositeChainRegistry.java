package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeFunctionRefBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeLoopRoundBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeValidationBO;
import com.aircas.ptr.foundry.ontology.model.enums.CompositeNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeLoopConfig;
import com.aircas.ptr.foundry.ontology.utils.CompositeElCompiler;
import com.aircas.ptr.foundry.ontology.utils.CompositeValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.flow.FlowBus;
import com.yomahub.liteflow.flow.LiteflowResponse;
import com.yomahub.liteflow.property.LiteflowConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 函数编排执行链注册中心。
 *
 * <p>把画布上的编排定义（{@link CompositeConfig}）编译成 LiteFlow 执行链并驱动执行：</p>
 * <ol>
 *   <li>校验：{@link CompositeValidator#validate}，被引用函数由 {@link CompositeFunctionRefResolver} 从库里解析；</li>
 *   <li>编译：{@link CompositeElCompiler#compile} 生成 EL 与逐节点运行期定义；</li>
 *   <li>注册：为每个节点创建组件实例（LiteFlow 的节点与实例一一对应，协作者只能构造器注入），
 *       再用 {@link LiteFlowChainELBuilder} 构建执行链；</li>
 *   <li>执行：{@link FlowExecutor#execute2Resp}，运行期数据由 {@link CompositeRuntimeRequest} 承载。</li>
 * </ol>
 *
 * <p>LiteFlow 的节点表与链表是 JVM 全局的，节点 id 由链名派生，因此同一份编排定义只需注册一次；
 * 编排内容变化时 EL 文本随之变化，重新注册即覆盖旧节点，旧链显式移除，避免全局表残留脏数据。</p>
 */
@Slf4j
@Component
public class CompositeChainRegistry {

    /** 空支路/空循环体占位节点 id，全局唯一并被所有链共用 */
    private static final String NOOP = CompositeElCompiler.NOOP_NODE_ID;

    /** 编译器生成的循环体取数节点类型 */
    private static final String LOOP_PRELOAD_KIND = CompositeElCompiler.LOOP_PRELOAD_KIND;

    /** 链名 -&gt; 已注册的 EL 文本，EL 未变化时跳过重复注册 */
    private final Map<String, String> installedEls = new ConcurrentHashMap<>();

    /** 链名 -&gt; 该链注册过的节点 id，用于删除函数时回收 */
    private final Map<String, Set<String>> nodesByChain = new ConcurrentHashMap<>();

    private final CompositeFunctionRefResolver refResolver;

    private final CompositeFunctionCatalog catalog;

    private final CompositeFunctionInvoker functionInvoker;

    private final CompositeTransformExecutor transformExecutor;

    private final ObjectMapper objectMapper;

    private final ObjectProvider<FlowExecutor> executorProvider;

    /** 无 Spring 上下文（如单元测试）时自建执行器 */
    private volatile FlowExecutor fallbackExecutor;

    public CompositeChainRegistry(CompositeFunctionRefResolver refResolver, CompositeFunctionCatalog catalog,
                                  CompositeFunctionInvoker functionInvoker, CompositeTransformExecutor transformExecutor,
                                  ObjectMapper objectMapper, ObjectProvider<FlowExecutor> executorProvider) {
        this.refResolver = refResolver;
        this.catalog = catalog;
        this.functionInvoker = functionInvoker;
        this.transformExecutor = transformExecutor;
        this.objectMapper = objectMapper;
        this.executorProvider = executorProvider;
    }

    /**
     * 校验编排定义。
     *
     * @param config 编排定义
     * @return 校验结果，含错误与告警文案
     */
    public CompositeValidationBO validate(CompositeConfig config) {
        return CompositeValidator.validate(config, refResolver.byFunctionRef());
    }

    /**
     * 校验并编译编排定义，返回 EL 与运行期节点定义（不注册执行链）。
     *
     * @param chainName 执行链名，通常是编排函数 api
     * @param config    编排定义
     * @return 编译结果
     */
    public CompositePlanBO compile(String chainName, CompositeConfig config) {
        CompositeValidationBO validation = validate(config);
        if (!validation.isValid()) {
            throw new BusinessException("函数编排校验不通过：" + String.join("；", validation.getErrors()));
        }
        Map<String, CompositeFunctionRefBO> refs = refResolver.resolveByCanvasNodeId(config);
        return CompositeElCompiler.compile(chainName, config, refs);
    }

    /**
     * 执行编排函数。
     *
     * @param functionApi 编排函数 api，同时作为执行链名的命名空间
     * @param config      编排定义
     * @param inputs      外部入参：参数名 -&gt; 值
     * @return 编排的外部出参
     */
    public Map<String, Object> execute(String functionApi, CompositeConfig config, Map<String, Object> inputs) {
        applyManualInputBindings(config);
        CompositePlanBO plan = compile(chainNameOf(functionApi), config);
        CompositeRuntimeRequest request = new CompositeRuntimeRequest(plan, inputs);
        synchronized (this) {
            install(plan, request);
        }
        String chainName = plan.getChainName();
        LiteflowResponse response = executor().execute2Resp(chainName, request, CompositeContext.class);
        if (!response.isSuccess()) {
            Throwable cause = response.getCause();
            log.error("函数编排[{}]执行失败，链={}，原因={}", functionApi, chainName,
                    cause == null ? response.getMessage() : cause.getMessage(), cause);
            throw new BusinessException("函数编排执行失败：" + describeCause(cause, response.getMessage()));
        }
        Map<String, Object> result = request.assembleOutput();
        log.info("函数编排[{}]执行完成，节点轨迹={}，出参={}", functionApi, request.getInvocations(), result);
        return result;
    }

    /**
     * 从画布 graph 中提取手动输入绑定（mode=manual），合并到对应节点的 inputDefaults。
     * <p>前端在编排画布上为节点配置"手动输入"时，固定值存储在 graph.nodes[].properties.inputBindings 中，
     * 而执行引擎使用的是 nodes[].inputDefaults。此方法在编译前将两者对齐，使手动输入值生效。</p>
     * <p>绑定可能指向嵌套字段（paramPath 长度 &gt; 1），此时需按路径构建嵌套 Map。</p>
     */
    @SuppressWarnings("unchecked")
    private void applyManualInputBindings(CompositeConfig config) {
        if (config == null || config.getGraph() == null || config.getNodes() == null) {
            return;
        }
        Object graphObj = config.getGraph();
        Map<String, Object> graph;
        try {
            ObjectMapper mapper = new ObjectMapper();
            graph = mapper.convertValue(graphObj, Map.class);
        } catch (Exception e) {
            log.warn("编排 graph 解析失败，跳出手动输入绑定提取", e);
            return;
        }
        List<Map<String, Object>> graphNodes = (List<Map<String, Object>>) graph.get("nodes");
        if (graphNodes == null) {
            return;
        }
        Map<String, com.aircas.ptr.foundry.ontology.model.param.CompositeNode> nodeById = new java.util.HashMap<>();
        for (com.aircas.ptr.foundry.ontology.model.param.CompositeNode node : config.getNodes()) {
            if (node != null && node.getId() != null) {
                nodeById.put(node.getId(), node);
            }
        }
        for (Map<String, Object> graphNode : graphNodes) {
            String nodeId = graphNode == null ? null : String.valueOf(graphNode.get("id"));
            if (nodeId == null || nodeId.equals("null")) {
                continue;
            }
            Map<String, Object> properties = (Map<String, Object>) graphNode.get("properties");
            if (properties == null) {
                continue;
            }
            List<Map<String, Object>> bindings = (List<Map<String, Object>>) properties.get("inputBindings");
            if (bindings == null) {
                continue;
            }
            com.aircas.ptr.foundry.ontology.model.param.CompositeNode node = nodeById.get(nodeId);
            if (node == null) {
                continue;
            }
            Map<String, Object> defaults = node.getInputDefaults();
            if (defaults == null) {
                defaults = new java.util.LinkedHashMap<>();
                node.setInputDefaults(defaults);
            }
            for (Map<String, Object> binding : bindings) {
                if (binding == null || !"manual".equals(String.valueOf(binding.get("mode")))) {
                    continue;
                }
                Object value = binding.get("value");
                if (value == null) {
                    continue;
                }
                List<String> paramPath = (List<String>) binding.get("paramPath");
                if (paramPath != null && paramPath.size() > 1) {
                    // 嵌套路径：按 path 逐层构建 Map，最后一层放 value
                    setNestedValue(defaults, paramPath, value);
                } else {
                    String paramName = String.valueOf(binding.get("paramName"));
                    if (paramName != null && !paramName.equals("null")) {
                        defaults.put(paramName, value);
                    }
                }
            }
        }
    }

    /**
     * 按嵌套路径写入值到 defaults Map 中。
     * <p>例如 path=["input","userName"], value="5" → defaults={"input": {"userName": "5"}}</p>
     */
    @SuppressWarnings("unchecked")
    private static void setNestedValue(Map<String, Object> defaults, List<String> path, Object value) {
        if (path == null || path.isEmpty()) {
            return;
        }
        Map<String, Object> current = defaults;
        for (int i = 0; i < path.size() - 1; i++) {
            String key = path.get(i);
            Object existing = current.get(key);
            if (existing instanceof Map) {
                current = (Map<String, Object>) existing;
            } else {
                Map<String, Object> nested = new java.util.LinkedHashMap<>();
                current.put(key, nested);
                current = nested;
            }
        }
        current.put(path.get(path.size() - 1), value);
    }

    /**
     * 移除编排函数已注册的执行链与节点（函数删除或定义变更时调用）。
     *
     * @param functionApi 编排函数 api
     */
    public synchronized void evict(String functionApi) {
        if (functionApi == null || functionApi.trim().isEmpty()) {
            return;
        }
        removeChain(chainNameOf(functionApi));
    }

    // ================================================================== 注册

    /** 注册节点与执行链；同一份定义（EL 文本相同）重复调用无副作用 */
    private void install(CompositePlanBO plan, CompositeRuntimeRequest request) {
        String chainName = plan.getChainName();
        String el = plan.getEl();
        boolean alreadyInstalled = el.equals(installedEls.get(chainName)) && FlowBus.containChain(chainName);
        if (!alreadyInstalled) {
            registerNoop();
            Set<String> nodeIds = new LinkedHashSet<>();
            nodeIds.add(NOOP);
            for (CompositeNodePlanBO nodePlan : plan.getNodes().values()) {
                nodeIds.add(nodePlan.getElId());
                registerNode(nodePlan);
            }
            // 取数节点由编译器生成、只存在于循环体 EL 中，画布上没有它，但同样要注册
            for (CompositeLoopRoundBO round : plan.getLoopRounds()) {
                if (nodeIds.add(round.getPreloadElId())) {
                    registerNode(round.getPreloadNode());
                }
            }
            // 画布节点 id 变更后，旧 id 若不再被任何链引用必须释放，否则全局节点表持续累积僵尸节点
            Set<String> previousIds = nodesByChain.put(chainName, nodeIds);
            releaseStaleNodes(previousIds, nodeIds);
            if (FlowBus.containChain(chainName)) {
                FlowBus.removeChain(chainName);
            }
            LiteFlowChainELBuilder.createChain().setChainName(chainName).setEL(el).build();
            installedEls.put(chainName, el);
            log.info("函数编排执行链已注册: 链={}, EL={}", chainName, el);
        }
        // 循环逐轮重放的内容只能在本次执行请求上登记：编译器已经给出每个循环的区域成员
        registerLoopRounds(plan, request);
    }

    /**
     * 把编译器登记的循环区域挂到本次执行请求上，供循环体取数节点逐轮重放"来自循环体外的上游取值"。
     * <p>登记键用取数节点 elId：它由编译器生成、带链名前缀，跨编排不会重复。</p>
     */
    private void registerLoopRounds(CompositePlanBO plan, CompositeRuntimeRequest request) {
        for (CompositeLoopRoundBO round : plan.getLoopRounds()) {
            request.registerLoopRound(round.getPreloadElId(), round.getMemberElIds());
        }
    }

    /** 按节点类型创建组件实例并注册到 LiteFlow 全局节点表 */
    private void registerNode(CompositeNodePlanBO nodePlan) {
        String elId = nodePlan.getElId();
        NodeComponent instance;
        if (LOOP_PRELOAD_KIND.equals(nodePlan.getKind())) {
            // 编译器为循环体生成的取数节点，画布上没有对应类型
            instance = new CompositeLoopPreloadComponent();
        } else {
            CompositeNodeKindEnum kind = CompositeNodeKindEnum.fromKind(nodePlan.getKind());
            if (kind == CompositeNodeKindEnum.LOOP) {
                instance = timesLoop(nodePlan) ? new CompositeForComponent() : new CompositeLoopComponent();
            } else if (kind == CompositeNodeKindEnum.BRANCH) {
                instance = new CompositeBranchComponent();
            } else if (kind == CompositeNodeKindEnum.SWITCH) {
                instance = new CompositeSwitchComponent();
            } else if (kind == CompositeNodeKindEnum.TRANSFORM) {
                instance = new CompositeTransformComponent(transformExecutor);
            } else if (kind == CompositeNodeKindEnum.START || kind == CompositeNodeKindEnum.END) {
                // 开始节点需要把外部输入传递给下游，结束节点需要收集输入并产生输出
                if (kind == CompositeNodeKindEnum.START) {
                    instance = new CompositeStartComponent();
                } else {
                    instance = new CompositeEndComponent();
                }
            } else {
                instance = new CompositeFunctionComponent(catalog, functionInvoker, objectMapper);
            }
        }
        // LiteFlow 在 initComponent 时读取 name 并固化到全局节点表，必须先设名再注册
        instance.setName(nodePlan.getLabel());
        FlowBus.addManagedNode(elId, instance);
    }

    /** 占位节点全局只注册一次 */
    private void registerNoop() {
        if (FlowBus.containNode(NOOP)) {
            return;
        }
        CompositeNoopComponent instance = new CompositeNoopComponent();
        instance.setName("空节点");
        FlowBus.addManagedNode(NOOP, instance);
    }

    private void removeChain(String chainName) {
        installedEls.remove(chainName);
        if (FlowBus.containChain(chainName)) {
            FlowBus.removeChain(chainName);
        }
        Set<String> nodeIds = nodesByChain.remove(chainName);
        if (nodeIds == null) {
            return;
        }
        for (String nodeId : nodeIds) {
            // 占位节点被所有链共用，其余节点 id 由链名派生而互不冲突
            if (NOOP.equals(nodeId) || stillUsed(nodeId)) {
                continue;
            }
            if (FlowBus.containNode(nodeId)) {
                FlowBus.removeNode(nodeId);
            }
        }
    }

    /** 释放上一次注册但本次定义已不再使用的节点 */
    private void releaseStaleNodes(Set<String> previousIds, Set<String> currentIds) {
        if (previousIds == null) {
            return;
        }
        for (String nodeId : previousIds) {
            if (currentIds.contains(nodeId) || NOOP.equals(nodeId) || stillUsed(nodeId)) {
                continue;
            }
            if (FlowBus.containNode(nodeId)) {
                FlowBus.removeNode(nodeId);
            }
        }
    }

    private boolean stillUsed(String nodeId) {
        for (Set<String> ids : nodesByChain.values()) {
            if (ids.contains(nodeId)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== 辅助

    /** 与 {@code CompositeElCompiler} 一致的循环模式判定：mode 为 times 时用 FOR 节点 */
    private static boolean timesLoop(CompositeNodePlanBO nodePlan) {
        CompositeLoopConfig loopConfig = nodePlan.getLoopConfig();
        String mode = loopConfig == null ? null : loopConfig.getMode();
        return mode != null && !mode.trim().isEmpty() && "times".equalsIgnoreCase(mode.trim());
    }

    private FlowExecutor executor() {
        FlowExecutor springExecutor = executorProvider.getIfAvailable();
        if (springExecutor != null) {
            return springExecutor;
        }
        FlowExecutor local = fallbackExecutor;
        if (local == null) {
            synchronized (this) {
                local = fallbackExecutor;
                if (local == null) {
                    LiteflowConfig config = new LiteflowConfig();
                    config.setPrintExecutionLog(false);
                    local = new FlowExecutor(config);
                    local.init(true);
                    fallbackExecutor = local;
                }
            }
        }
        return local;
    }

    /** 链名归一化为合法 EL 标识符 */
    public static String chainNameOf(String functionApi) {
        return CompositeElCompiler.normalizeElId(functionApi);
    }

    private static String describeCause(Throwable cause, String fallbackMessage) {
        if (cause == null) {
            return fallbackMessage == null || fallbackMessage.trim().isEmpty() ? "未知原因" : fallbackMessage;
        }
        String message = cause.getMessage();
        return message == null || message.trim().isEmpty() ? cause.getClass().getSimpleName() : message;
    }

    @PreDestroy
    public void destroy() {
        synchronized (this) {
            List<String> chainNames = new ArrayList<>(installedEls.keySet());
            for (String chainName : chainNames) {
                removeChain(chainName);
            }
            if (FlowBus.containNode(NOOP)) {
                FlowBus.removeNode(NOOP);
            }
            log.info("函数编排执行链已释放: {} 条", chainNames.size());
        }
    }
}
