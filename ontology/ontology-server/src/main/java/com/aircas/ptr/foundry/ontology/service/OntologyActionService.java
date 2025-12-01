package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.common.constant.ActionRuleConnectType;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleCommitFlashMemory;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.exception.*;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionVO;
import com.aircas.ptr.foundry.ontology.model.vo.ParameterMetadataVO;
import com.aircas.ptr.foundry.ontology.model.param.ActionHandleMappingInParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionHandleRuleAddParam;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.github.pagehelper.PageInfo;

import java.util.Date;
import java.util.List;

public interface OntologyActionService extends IService<OntologyAction> {

    void removeByOntologyIdentifier(String ontologyIdentifier);

    //int save(OntologyActionBo ontologyActionBo) throws OntologyFunctionParameterPropertyTypeNotSameException, FunctionFileNotCompiled, FunctionNotFoundException, FunctionClassNotNewInstanceException, OntologyFunctionBindingParameterNotFoundException, OntologyFunctionMappedPropertyNotFoundException;

    List<ParameterMetadataVO> getParametersByApi(String actionApi) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionNotFoundException;

    OntologyActionVO getMetadataByApi(String apiName) throws OntologyFunctionNotFoundException, OntologyFunctionMappedPropertyNotFoundException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException;

    List<OntologyActionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) throws OntologyFunctionMappedPropertyNotFoundException, FunctionFileNotCompiled, FunctionNotFoundException, FunctionClassNotNewInstanceException;

    Object handle(String primaryKey , String api, List<ActionHandleMappingInParam> params) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException, OntologyFunctionNotFoundException, OntologyApiNameNotFoundException, OntologyFunctionMappedPropertyNotFoundException;

    int delete(long id);

    PageInfo<OntologyActionVO> metaList(Integer page, Integer size);

    int update(OntologyActionBo ontologyActionBo);

    void handleTask(Long actionHandleTaskId);

    int getCountByStatus(int status);

    boolean configRule(String actionApi, List<String> objectPrimaryKeys, List<ActionHandleRuleAddParam> rules, ActionRuleConnectType ruleConnectType);

    List<ActionHandleRule> getActionRulesById(List<Long> ids);

    int updateActionRulesById(ActionHandleRule rule);

    ActionHandleCommitFlashMemory getActionDataLogByAction(long actionId,String primaryValue);

    int updateActionDataById(ActionHandleCommitFlashMemory flashMemory);

    boolean configTask(String actionApi, List<String> objectPrimaryKeys, Date taskStartTime, Date taskEndTime, String taskCorn);

    void createAction(ActionCreateOrUpdateParam param);

    void updateAction(ActionCreateOrUpdateParam param);

    void deleteActionByApi(String actionApi);

    Page<OntologyActionInfoVO> pageGetActionByOntologyId(String ontologyUniqIdentifier, Integer pageNum, Integer pageSize);

    OntologyActionDetailVO getActionByApi(String actionApi);
}
