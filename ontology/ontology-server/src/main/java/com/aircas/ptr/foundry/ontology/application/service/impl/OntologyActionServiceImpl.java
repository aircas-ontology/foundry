package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.*;
import com.aircas.ptr.foundry.ontology.Exception.*;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.entity.bo.ActionRequestBodyBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyBaseObjectBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionMappingInBO;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyFunctionMappingInMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;

import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OntologyActionServiceImpl implements OntologyActionService {

    @Resource
    private final OntologyActionMapper ontologyActionMapper;

    @Resource
    private final OntologyFunctionMappingInMapper ontologyFunctionMappingInMapper;

    @Resource
    private final OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private final OntologyPropertyMapper propertyMapper;

    @Resource
    private final ObjectService objectService;

    @Resource
    private final OntologyPropertyService ontologyPropertyService;

    @Resource
    FunctionService functionService;

    static String ontologySelfIdentifier = "-1";

    static String currentObjectDesc = "当前本体对象";

    @Override
    public Object handle(ActionRequestBodyBO actionRequestBodyBO)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException, OntologyFunctionNotFoundException, OntologyApiNameNotFoundException {
        HashMap<String, Object> parameters = actionRequestBodyBO.getParameters();
        OntologyBaseObjectBo currentObject = actionRequestBodyBO.getCurrentObject();
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByApi(currentObject.getApi());
        if (ontologyMeta == null) {
            throw ExceptionFactory.getOntologyApiNameNotFoundException(null);
        }
        OntologyActionBo ontologyFunctionBo = queryByApi(
                actionRequestBodyBO.getFunctionName(),
                actionRequestBodyBO.isPreview()
        );
        String originalFunction = ontologyFunctionBo.getFunctionApi();
        //根据mapping结果，把property注入到parameters
        List<OntologyFunctionMappingInBO> mappingInList = ontologyFunctionBo.getMappingInList();
        ObjectOneInfoVO objectOneInfoVO = objectService.queryObjectByPrimaryKey(ontologyMeta.getUniqueIdentifier(), currentObject.getPrimaryKey());
        List<PropertyValueVO> propertyList = objectOneInfoVO.getProperties();
        Map<String, PropertyValueVO> propertyMap = new HashMap<>();
        propertyList.forEach(propertyValueVO -> {
            propertyMap.put(propertyValueVO.getUniqueIdentifier(), propertyValueVO);
        });
        for (OntologyFunctionMappingInBO mappingIn : mappingInList) {
            String parameterName = mappingIn.getParameterName();
            String propertyUniqueIdentifier = mappingIn.getPropertyUniqueIdentifier();
            if (ontologySelfIdentifier.equals(propertyUniqueIdentifier)) {
                parameters.put(parameterName, actionRequestBodyBO.getCurrentObject());
            } else {
                PropertyValueVO propertyValueVO = propertyMap.get(propertyUniqueIdentifier);
                String value = propertyValueVO.getValue();
                parameters.put(parameterName, value);
            }
        }
        //根据property的值，设置参数的值即可，如果是当前对象，则设置为当前对象，也就是currentObject即可，包含api 和primaryKey
        Object result = functionService.handle(originalFunction, false, null, parameters);
        if (actionRequestBodyBO.isPreview()) {
            //如果是在preview模式下，执行后，就删除
            delete(ontologyFunctionBo.getId());
        }
        return result;
    }

    private OntologyActionBo queryByApi(String functionApi, boolean isPreview) throws OntologyFunctionNotFoundException {

        OntologyAction ontologyAction = ontologyActionMapper.selectByApi(functionApi, isPreview);
        OntologyActionBo ontologyFunctionBo = new OntologyActionBo();
        if (ontologyAction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        BeanUtils.copyProperties(ontologyAction, ontologyFunctionBo);
        List<OntologyFunctionMappingIn> mappingInList = ontologyFunctionMappingInMapper.selectByOntologyFunctionId(ontologyAction.getId());
        List<OntologyFunctionMappingInBO> mappingInBoList = mappingInList.stream().map(ontologyFunctionMappingIn -> {
            OntologyFunctionMappingInBO ontologyFunctionMappingInBo = new OntologyFunctionMappingInBO();
            BeanUtils.copyProperties(ontologyFunctionMappingIn, ontologyFunctionMappingInBo);
            return ontologyFunctionMappingInBo;
        }).collect(Collectors.toList());
        ontologyFunctionBo.setMappingInList(mappingInBoList);
        return ontologyFunctionBo;
    }

    @Override
    public int save(OntologyActionBo ontologyActionBo)
            throws OntologyFunctionParameterPropertyTypeNotSameException, FunctionFileNotCompiled, FunctionNotFoundException,
            FunctionClassNotNewInstanceException, OntologyFunctionBindingParameterNotFoundException, OntologyFunctionMappedPropertyNotFoundException {

        checkBindingConsistence(ontologyActionBo);
        OntologyAction ontologyAction = new OntologyAction();
        BeanUtils.copyProperties(ontologyActionBo, ontologyAction);
        ontologyAction.setCreateTime(new Date());
        ontologyAction.setUpdateTime(new Date());
        int status = ontologyActionMapper.insert(ontologyAction);
        long id = ontologyAction.getId();
        for (OntologyFunctionMappingInBO mappingInBO : ontologyActionBo.getMappingInList()) {
            OntologyFunctionMappingIn mappingIn = new OntologyFunctionMappingIn();
            BeanUtils.copyProperties(mappingInBO, mappingIn);
            mappingIn.setOntologyFunctionId(id);
            mappingIn.setCreateTime(new Date());
            mappingIn.setUpdateTime(new Date());
            status = ontologyFunctionMappingInMapper.insert(mappingIn);
        }
        return status;
    }

    private void checkBindingConsistence(OntologyActionBo ontologyFunctionBo)
            throws OntologyFunctionParameterPropertyTypeNotSameException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionBindingParameterNotFoundException, OntologyFunctionMappedPropertyNotFoundException {

        String ontologUniqueIdentifier = ontologyFunctionBo.getOntologyUniqueIdentifier();
        List<OntologyPropertyVO> propertyVOS = ontologyPropertyService.selectByOntologyUniqueIdentifier(ontologUniqueIdentifier);
        Map<String, OntologyPropertyVO> propertyMap = new HashMap<>();
        propertyVOS.forEach(propertyVO -> {
            propertyMap.put(propertyVO.getUniqueIdentifier(), propertyVO);
        });

        List<ParameterMetadataVO> parameterMetadataVOS = functionService.getParameters(ontologyFunctionBo.getFunctionApi());
        Map<String, ParameterMetadataVO> parameterMetadataMap = new HashMap<>();
        parameterMetadataVOS.forEach(parameterMetadataVO -> {
            parameterMetadataMap.put(parameterMetadataVO.getName(), parameterMetadataVO);
        });

        for (OntologyFunctionMappingInBO mappingInBO : ontologyFunctionBo.getMappingInList()) {
            String parameterName = mappingInBO.getParameterName();
            String propertyUniqueIdentifier = mappingInBO.getPropertyUniqueIdentifier();
            if (ontologySelfIdentifier.equals(propertyUniqueIdentifier)) {

            } else {
                ParameterMetadataVO parameterMetadataVO = parameterMetadataMap.get(parameterName);
                if (parameterMetadataVO == null) {
                    throw ExceptionFactory.getOntologyFunctionBindingParameterNotFoundException(null);
                }
                OntologyPropertyVO ontologyPropertyVO = propertyMap.get(propertyUniqueIdentifier);
                if (ontologyPropertyVO == null) {
                    throw ExceptionFactory.getOntologyFunctionMappedPropertyNotFoundException(null);
                }
                if (!parameterMetadataVO.getType().equals(ontologyPropertyVO.getPropertyType().name())) {
                    throw ExceptionFactory.getOntologyFunctionParameterPropertyTypeNotSameException(null);
                }
            }
        }
    }

    @Override
    public OntologyActionVO getMetadataByApi(String apiName)

            throws OntologyFunctionMappedPropertyNotFoundException, OntologyFunctionNotFoundException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException {
        OntologyAction ontologyAction = ontologyActionMapper.selectByApi(apiName, true);
        if (ontologyAction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyAction.getOntologyUniqueIdentifier());
        Map<String, OntologyPropertyVO> ontologyPropertiesMap = queryPropertiesByOntologyUniqueIdentifier(ontologyAction.getOntologyUniqueIdentifier());
        List<OntologyFunctionMappingIn> allMappings = ontologyFunctionMappingInMapper.selectByOntologyFunctionId(ontologyAction.getId());
        return getFunctionVO(ontologyAction, ontologyMeta, allMappings, ontologyPropertiesMap);
    }

    private Map<String, OntologyPropertyVO> queryPropertiesByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {
        Map<String, OntologyPropertyVO> ontologyPropertiesMap = new HashMap<>();
        List<OntologyPropertyVO> ontologyProperties = ontologyPropertyService.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        ontologyProperties.forEach(ontologyProperty -> {
            ontologyPropertiesMap.put(ontologyProperty.getUniqueIdentifier(), ontologyProperty);
        });
        return ontologyPropertiesMap;
    }


    public List<ParameterMetadataVO> getParametersByApi(String functionApi)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionNotFoundException {

        OntologyAction ontologyAction = ontologyActionMapper.selectByApi(functionApi, true);
        if (ontologyAction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        List<ParameterMetadataVO> parameterMetadataVOList = functionService.getParameters(ontologyAction.getFunctionApi());
        List<OntologyFunctionMappingIn> mappingIns = ontologyFunctionMappingInMapper.selectByOntologyFunctionId(ontologyAction.getId());
        List<String> mappedParameters = mappingIns.stream().map(ontologyFunctionMappingIn -> ontologyFunctionMappingIn.getParameterName()).collect(Collectors.toList());
        parameterMetadataVOList.removeIf(parameterMetadataVO -> mappedParameters.contains(parameterMetadataVO.getName()));
        return parameterMetadataVOList;
    }


    @Override
    public List<OntologyActionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier)
            throws OntologyFunctionMappedPropertyNotFoundException, FunctionFileNotCompiled, FunctionNotFoundException, FunctionClassNotNewInstanceException {
        List<OntologyAction> list = ontologyActionMapper.selectByOntologyIdentifier(ontologyUniqueIdentifier);
        List<Long> functionIds = list.stream().map(ontologyAction -> ontologyAction.getId()).collect(Collectors.toList());
        List<OntologyFunctionMappingIn> allMappings = ontologyFunctionMappingInMapper.selectByOntologyFunctionIds(functionIds);

        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyUniqueIdentifier);
        Map<String, OntologyPropertyVO> ontologyPropertiesMap = queryPropertiesByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        List<OntologyActionVO> result = new ArrayList();
        for (OntologyAction function : list) {
            OntologyActionVO ontologyFunctionVO = getFunctionVO(function, ontologyMeta, allMappings, ontologyPropertiesMap);
            result.add(ontologyFunctionVO);
        }
        return result;
    }

    private OntologyActionVO getFunctionVO(OntologyAction function,
                                           OntologyMeta ontologyMeta,
                                           List<OntologyFunctionMappingIn> allMappingIns,
                                           Map<String, OntologyPropertyVO> ontologyPropertiesMap
    ) throws OntologyFunctionMappedPropertyNotFoundException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException {

        OntologyActionVO ontologyFunctionVO = new OntologyActionVO();
        BeanUtils.copyProperties(function, ontologyFunctionVO);
        ontologyFunctionVO.setOntologyDisplayName(ontologyMeta.getDisplayName());

        List<OntologyFunctionMappingIn> mappingInList = allMappingIns
                .stream()
                .filter(ontologyFunctionMappingIn -> ontologyFunctionMappingIn.getOntologyFunctionId() == ontologyFunctionVO.getId())
                .collect(Collectors.toList());
        List<OntologyFunctionMappingInVO> mappingInVOs = new ArrayList<>();
        String ontologyType = StringUtils.capitalize(ontologyMeta.getApiName());
        for (OntologyFunctionMappingIn mapping : mappingInList) {
            boolean isOntologySelf = ontologySelfIdentifier.equals(mapping.getPropertyUniqueIdentifier());
            OntologyFunctionMappingInVO mappingInVO = new OntologyFunctionMappingInVO();
            BeanUtils.copyProperties(mapping, mappingInVO);
            if (!isOntologySelf) {
                OntologyPropertyVO ontologyPropertyVO = ontologyPropertiesMap.get(mapping.getPropertyUniqueIdentifier());
                if (ontologyPropertyVO == null) {
                    throw ExceptionFactory.getOntologyFunctionMappedPropertyNotFoundException(null);
                }
                mappingInVO.setPropertyName(ontologyPropertyVO.getDisplayName());
                mappingInVO.setPropertyType(ontologyPropertyVO.getPropertyType() != null ? ontologyPropertyVO.getPropertyType().name() : null);
            } else {
                mappingInVO.setPropertyName(currentObjectDesc);
                mappingInVO.setPropertyType(ontologyType);
            }
            List<ParameterMetadataVO> parameterMetadataVOList = functionService.getParameters(function.getFunctionApi());
            List<ParameterMetadataVO> matchedParaList = parameterMetadataVOList.stream()
                    .filter(parameterMetadataVO -> parameterMetadataVO.getName().equals(mappingInVO.getParameterName()))
                    .collect(Collectors.toList());
            if (matchedParaList.size() >= 1) {
                if (isOntologySelf) {
                    mappingInVO.setParameterType(ontologyType);
                } else {
                    mappingInVO.setParameterType(matchedParaList.get(0).getType());
                }
            }
            mappingInVOs.add(mappingInVO);
        }
        ontologyFunctionVO.setMappingInList(mappingInVOs);
        return ontologyFunctionVO;
    }

    @Override
    public int delete(long id) {

        // TODO:未实现
        return 0;
    }

}
