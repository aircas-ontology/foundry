package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

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

    void deleteActionByApi(Integer ontologySpaceId, String actionApi);

    Page<OntologyActionInfoVO> pageGetActionByOntologyId(Integer ontologySpaceId, String ontologyId, Integer categoryId, String keyword, Integer status, Integer pageNum, Integer pageSize);

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

    Long createScheduling(ActionSchedulingCreateParam param);

    void updateScheduling(ActionSchedulingUpdateParam param);

    void removeScheduling(ActionSchedulingTypeEnum type, Long id);

    void startScheduling(ActionSchedulingTypeEnum type, Long id);

    void stopScheduling(ActionSchedulingTypeEnum type, Long id);

    ActionSchedulingDetailVO getSchedulingDetailById(Long id, ActionSchedulingTypeEnum type);

    Page<ActionSchedulingInfoVO> listScheduling(Integer pageNum, Integer pageSize);

    Page<SchedulingResultVO> getSchedulingResult(Long id, ActionSchedulingTypeEnum type, Integer pageNum, Integer pageSize);
}
