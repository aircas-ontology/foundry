package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.AgentOntologyBuildParam;
import com.aircas.ptr.foundry.ontology.model.vo.AgentOntologyBuildResultVO;

/**
 * Agent 本体构建落库服务。
 *
 * <p>对应 Agent 对话流程的「最终落库」阶段：把功能二（对象定义）、功能三（属性选择）、
 * 功能四（关系选择）的汇总结果一次性写入本体库。编排方式模仿画布一键建空间
 * （{@code OntologySpaceService#createSpaceWithCanvasContent}），区别在于**不创建新空间**——
 * 空间已由用户选定，仅在既有空间下创建一个本体对象及其属性、关系。</p>
 *
 * <p>整个落库过程在单一事务内完成（{@code chainedTransactionManager}），任一环节失败整体回滚，
 * 避免产生"有对象无属性"或"有关系无对象"的脏数据。</p>
 */
public interface AgentOntologyBuildService {

    /**
     * 在既有空间下创建一个本体对象，并级联落库其属性与关系。
     *
     * @param param 落库入参（对象定义 + 属性列表 + 关系列表）
     * @return 落库结果（新本体唯一标识及属性/关系数量统计）
     */
    AgentOntologyBuildResultVO buildOntology(AgentOntologyBuildParam param);
}
