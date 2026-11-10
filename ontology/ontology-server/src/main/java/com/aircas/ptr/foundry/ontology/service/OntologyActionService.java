package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.TreeBatchExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.TreeExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface OntologyActionService extends IService<OntologyAction> {

    void removeByOntologyIdentifier(String ontologyIdentifier);

    void createAction(ActionCreateOrUpdateParam param);

    void updateAction(ActionCreateOrUpdateParam param);

    /**
     * 发布行为：草稿的行为置为已发布，才对智能体和调度可用。
     * 绑算子的行为要求算子已发布，否则执行时取不到可用版本。
     *
     * @param ontologySpaceId 对象空间id
     * @param actionApi       行为 api
     */
    void publishAction(Integer ontologySpaceId, String actionApi);

    /**
     * 撤回发布：已发布的行为退回草稿状态。
     *
     * @param ontologySpaceId 对象空间id
     * @param actionApi       行为 api
     */
    void unpublishAction(Integer ontologySpaceId, String actionApi);

    void deleteActionByApi(Integer ontologySpaceId, String actionApi);

    /**
     * 发布或下线行为树。行为没有版本，状态就是唯一开关；发布前会用库里的树定义重新校验一次。
     * <p>行为 api 只在空间内唯一，发布必须带空间定位，避免跨空间同名树被误改状态。</p>
     *
     * @param ontologySpaceId 行为树所属的对象空间id
     * @param actionApi       行为树的 api
     * @param status          1 发布，0 下线
     */
    void publishTree(Integer ontologySpaceId, String actionApi, Integer status);

    /**
     * 分页查行为/行为树列表。
     *
     * @param actionKinds 行为类别，多选；不传查全部类别
     */
    Page<OntologyActionInfoVO> pageGetActionByOntologyId(Integer ontologySpaceId, String ontologyId, Integer categoryId,
                                                         String keyword, Integer status, List<ActionKindEnum> actionKinds,
                                                         Integer pageNum, Integer pageSize);

    OntologyActionDetailVO getActionByApi(Integer ontologySpaceId, String actionApi);

    String executeAction(OntologyActionExecuteParam param);

    /**
     * 执行不绑算子的基础行为（属性值 / 实例关系）：按行为定义对单个实体真实落库。
     * 行为测试与实例执行共用这套执行体，调用方需带齐该行为定位数据所需的键
     * （属性值行为的分组数据主键、实例关系行为的对端实体主键）。
     *
     * @param param    单实体执行入参
     * @param infoDTO  已初始化的行为上下文，取其中的行为定义与外部输入参数
     * @return 本次落库动作说明
     */
    String executeBaseAction(EntityActionExecuteParam param, ActionContextInfoDTO infoDTO);

    /**
     * 行为测试：用一条真实实例数据把行为跑通并落库，用于发布前验证定义是否可用。
     *
     * @param param 行为 api + 本端/对端实体主键 + 外部输入参数取值
     * @return 本次落库动作说明；绑算子的行为附带算子返回结果
     */
    ActionTestResultVO testAction(ActionTestParam param);

    /**
     * 行为树执行：一次树执行只针对一条实例数据，全树共用它，节点跑通即真实落库。
     * <p>
     * 单轮求值，不产生 RUNNING；循环、冷却、限时、并行都在单次请求内生效。
     *
     * @param param 树 api + 实例主键 + 黑板键的执行入参
     * @return 整树状态 + 结束时黑板快照 + 逐节点结果
     */
    TreeExecuteResultVO executeTree(TreeExecuteParam param);

    /**
     * 行为树测试：草稿树也可以试跑，用于发布前验证编排；与正式执行一样真实落库。
     *
     * @param param 树 api + 实例主键 + 黑板键的执行入参
     * @return 整树状态 + 结束时黑板快照 + 逐节点结果
     */
    TreeExecuteResultVO testTree(TreeExecuteParam param);

    /**
     * 批量执行行为树：按对象类型遍历实例数据，每条实例独立跑一遍整棵树。
     * <p>单条实例失败不中断整批；明细默认只返回失败实例，需要成功明细时由 includeSuccess 打开。</p>
     *
     * @param param 树 api + 要遍历的对象类型 + 分片参数 + 共用的黑板入参
     * @return 本次执行的实例数、成功失败计数与失败明细
     */
    TreeBatchExecuteResultVO executeTreeBatch(TreeBatchExecuteParam param);

    /**
     * 行为树编辑器目录：节点类型清单 + 本空间内可作为行为节点绑定的已发布行为。
     *
     * @param ontologySpaceId 对象空间id，可绑定行为只取该空间
     */
    TreeDefsVO getTreeDefs(Integer ontologySpaceId);

    Long createScheduling(ActionSchedulingCreateParam param);

    void updateScheduling(ActionSchedulingUpdateParam param);

    void removeScheduling(ActionSchedulingTypeEnum type, Long id);

    void startScheduling(ActionSchedulingTypeEnum type, Long id);

    void stopScheduling(ActionSchedulingTypeEnum type, Long id);

    ActionSchedulingDetailVO getSchedulingDetailById(Long id, ActionSchedulingTypeEnum type);

    Page<ActionSchedulingInfoVO> listScheduling(Integer pageNum, Integer pageSize, Integer spaceId);

    Page<SchedulingResultVO> getSchedulingResult(Long id, ActionSchedulingTypeEnum type, Integer pageNum, Integer pageSize);

    /**
     * 手动触发规则调度：加载规则 DSL，逐实例评估，命中则执行绑定行为并写调用日志。
     *
     * @param id 规则调度id
     * @return 评估与执行摘要
     */
    RuleTriggerResultVO triggerRuleScheduling(Long id);
}
