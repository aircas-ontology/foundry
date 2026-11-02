package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingUpdateParam;
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

    void deleteActionByApi(String actionApi);

    Page<OntologyActionInfoVO> pageGetActionByOntologyId(Integer ontologySpaceId, String keyword, Integer status, Integer pageNum, Integer pageSize);

    OntologyActionDetailVO getActionByApi(String actionApi);

    String executeAction(OntologyActionExecuteParam param);

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
