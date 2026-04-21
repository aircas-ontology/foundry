package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingUpdateParam;
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

    Page<OntologyActionInfoVO> pageGetActionByOntologyId(String ontologyUniqIdentifier, Integer pageNum, Integer pageSize);

    OntologyActionDetailVO getActionByApi(String actionApi);

    String executeAction(OntologyActionExecuteParam param);

    Long createScheduling(ActionSchedulingCreateParam param);

    void updateScheduling(ActionSchedulingUpdateParam param);

    void removeScheduling(ActionSchedulingTypeEnum type, Long id);

    void startScheduling(ActionSchedulingTypeEnum type, Long id);

    void stopScheduling(ActionSchedulingTypeEnum type, Long id);

    ActionSchedulingDetailVO getSchedulingDetailById(Long id, ActionSchedulingTypeEnum type);

    Page<ActionSchedulingInfoVO> listScheduling(Integer pageNum, Integer pageSize);

    Page<SchedulingResultVO> getSchedulingResult(Long id, ActionSchedulingTypeEnum type, Integer pageNum, Integer pageSize);
}
