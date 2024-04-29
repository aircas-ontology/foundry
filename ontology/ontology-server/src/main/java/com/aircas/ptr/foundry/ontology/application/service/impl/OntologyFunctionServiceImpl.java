package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.util.BeanUtil;
import com.aircas.ptr.foundry.model.po.*;
import com.aircas.ptr.foundry.ontology.Exception.*;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyFunctionService;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionRequestBodyBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyBaseObjectBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionMappingInBO;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyFunctionMappingInMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyFunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;

import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.github.jsonldjava.utils.Obj;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OntologyFunctionServiceImpl implements OntologyFunctionService {

    @Resource
    private final OntologyFunctionMapper ontologyFunctionMapper;

    @Resource
    private final OntologyFunctionMappingInMapper ontologyFunctionMappingInMapper;

    @Resource
    private final OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private final OntologyPropertyMapper propertyMapper;

    @Resource
    private final ObjectService objectService;

    @Resource
    FunctionService functionService;

    static String ontologySelfIdentifier = "-1";

    @Override
    public Object handle(FunctionRequestBodyBO functionRequestBodyBO)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException, OntologyFunctionNotFoundException {
        HashMap<String, Object> parameters = functionRequestBodyBO.getParameters();
        OntologyBaseObjectBo currentObject = functionRequestBodyBO.getCurrentObject();
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByApi(currentObject.getApi());
        OntologyFunctionBo ontologyFunctionBo = queryByOntologyIdentifierAndApi(
                functionRequestBodyBO.getFunctionName(),
                ontologyMeta.getUniqueIdentifier(),
                functionRequestBodyBO.isPreview()
        );
        String originalFunction = ontologyFunctionBo.getOriginalApi();
        //根据mapping结果，把property注入到parameters
        List<OntologyFunctionMappingInBO> mappingInList = ontologyFunctionBo.getMappingInList();
        ObjectValueVo objectValueVo = objectService.queryObjectByPrimaryKey( ontologyMeta.getUniqueIdentifier(), currentObject.getPrimaryKey());
        List<PropertyValueVO> propertyList = objectValueVo.getProperties();
        Map<String, PropertyValueVO> propertyMap = new HashMap<>();
        propertyList.forEach(propertyValueVO -> {
            propertyMap.put(propertyValueVO.getUniqueIdentifier(), propertyValueVO);
        });
        for (OntologyFunctionMappingInBO mappingIn : mappingInList) {
            String parameterName = mappingIn.getParameterName();
            String propertyUniqueIdentifier = mappingIn.getPropertyUniqueIdentifier();
            if (ontologySelfIdentifier.equals(propertyUniqueIdentifier)) {
                parameters.put(parameterName, functionRequestBodyBO.getCurrentObject());
            } else {
                PropertyValueVO propertyValueVO = propertyMap.get(propertyUniqueIdentifier);
                String value = propertyValueVO.getValue();
                parameters.put(parameterName, value);
            }
        }
        //根据property的值，设置参数的值即可，如果是当前对象，则设置为当前对象，也就是currentObject即可，包含api 和primaryKey
        Object result = functionService.handle(originalFunction,false,null, parameters);
        if (functionRequestBodyBO.isPreview()) {
            //如果是在preview模式下，执行后，就删除
            delete(ontologyFunctionBo.getId());
        }
        return result;
    }

    private OntologyFunctionBo queryByOntologyIdentifierAndApi(String functionApi, String ontologyUniqueIdentifier, boolean isPreview) throws OntologyFunctionNotFoundException {
        OntologyFunction ontologyFunction = ontologyFunctionMapper.selectByOntologyIdentifierAndApi(ontologyUniqueIdentifier, functionApi, isPreview);
        OntologyFunctionBo ontologyFunctionBo = new OntologyFunctionBo();
        if (ontologyFunction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        BeanUtils.copyProperties(ontologyFunction, ontologyFunctionBo);
        List<OntologyFunctionMappingIn> mappingInList = ontologyFunctionMappingInMapper.selectByOntologyFunctionId(ontologyFunction.getId());
        List<OntologyFunctionMappingInBO> mappingInBoList = mappingInList.stream().map(ontologyFunctionMappingIn -> {
            OntologyFunctionMappingInBO ontologyFunctionMappingInBo = new OntologyFunctionMappingInBO();
            BeanUtils.copyProperties(ontologyFunctionMappingIn, ontologyFunctionMappingInBo);
            return ontologyFunctionMappingInBo;
        }).collect(Collectors.toList());
        ontologyFunctionBo.setMappingInList(mappingInBoList);
        return ontologyFunctionBo;
    }

    @Override
    public int save(OntologyFunctionBo ontologyFunctionBo) {
        OntologyFunction ontologyFunction = new OntologyFunction();
        BeanUtils.copyProperties(ontologyFunctionBo, ontologyFunction);
//        if (ontologyFunctionBo.isPreview()) {
//            ontologyFunction.setIsPreview(1);
//        } else {
//            ontologyFunction.setIsPreview(0);
//        }
        int status = ontologyFunctionMapper.insert(ontologyFunction);
        long id = ontologyFunction.getId();
        for (OntologyFunctionMappingInBO mappingInBO: ontologyFunctionBo.getMappingInList()) {
            OntologyFunctionMappingIn mappingIn = new OntologyFunctionMappingIn();
            BeanUtils.copyProperties(mappingInBO, mappingIn);
            mappingIn.setOntologyFunctionId(id);
            status = ontologyFunctionMappingInMapper.insert(mappingIn);
        }
        return status;
    }

    @Override
    public OntologyFunctionVO getMetadata(String functionApi, String ontologyUniqueIdentifier, boolean isPreview)
            throws OntologyFunctionMappedPropertyNotFoundException, OntologyFunctionNotFoundException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException {
        OntologyFunction ontologyFunction = ontologyFunctionMapper.selectByOntologyIdentifierAndApi(ontologyUniqueIdentifier, functionApi, isPreview);
        if (ontologyFunction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        OntologyFunctionBo ontologyFunctionBo = queryByOntologyIdentifierAndApi(functionApi, ontologyUniqueIdentifier, false);
        OntologyFunctionVO ontologyFunctionVO = new OntologyFunctionVO();
        BeanUtils.copyProperties(ontologyFunctionBo, ontologyFunctionVO, "mappingInList");
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyUniqueIdentifier);
        ontologyFunctionVO.setOntologyDisplayName(ontologyMeta.getDisplayName());
        //对mapping进行设置
        List<OntologyProperty> ontologyProperties =  this.propertyMapper.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        Map<String, OntologyProperty> ontologyPropertiesMap = new HashMap<>();
        ontologyProperties.forEach(ontologyProperty -> {
            ontologyPropertiesMap.put(ontologyProperty.getUniqueIdentifier(), ontologyProperty);
        });

        List<OntologyFunctionMappingInVO> mappingInVOs = new ArrayList<>();
        List<ParameterMetadataVO> parameterMetadataVOList = functionService.getParameters(ontologyFunction.getOriginalApi(), false, null);

        for(OntologyFunctionMappingInBO mapping:  ontologyFunctionBo.getMappingInList()) {
            OntologyFunctionMappingInVO mappingInVO = new OntologyFunctionMappingInVO();
            BeanUtils.copyProperties(mapping, mappingInVO);
            if (!ontologySelfIdentifier.equals(mapping.getPropertyUniqueIdentifier())) {
                OntologyProperty ontologyProperty = ontologyPropertiesMap.get(mapping.getPropertyUniqueIdentifier());
                if (ontologyProperty == null) {
                    throw ExceptionFactory.getOntologyFunctionMappedPropertyNotFoundException(null);
                }
                mappingInVO.setPropertyName(ontologyProperty.getDisplayName());
                mappingInVO.setPropertyType(ontologyProperty.getPropertyType() != null ? ontologyProperty.getPropertyType().name() : null);
                List<ParameterMetadataVO> matchedParaList = parameterMetadataVOList.stream()
                        .filter(parameterMetadataVO -> parameterMetadataVO.getName().equals(mappingInVO.getParameterName()))
                        .collect(Collectors.toList());
                if (matchedParaList.size() >= 1) {
                    mappingInVO.setParameterType(matchedParaList.get(0).getType());
                }
            }
            mappingInVOs.add(mappingInVO);
        }
        ontologyFunctionVO.setMappingInList(mappingInVOs);
        return ontologyFunctionVO;
    }


    public List<ParameterMetadataVO> getParameters(String functionApi, String ontologyUniqueIdentifier, boolean isPreview)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionNotFoundException {
        OntologyFunction ontologyFunction = ontologyFunctionMapper.selectByOntologyIdentifierAndApi(ontologyUniqueIdentifier, functionApi, isPreview);
        if (ontologyFunction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        List<ParameterMetadataVO> parameterMetadataVOList = functionService.getParameters(ontologyFunction.getOriginalApi(), false, null);
        List<OntologyFunctionMappingIn> mappingIns = ontologyFunctionMappingInMapper.selectByOntologyFunctionId(ontologyFunction.getId());
        List<String> mappedParameters =  mappingIns.stream().map(ontologyFunctionMappingIn -> ontologyFunctionMappingIn.getParameterName()).collect(Collectors.toList());
        parameterMetadataVOList.removeIf(parameterMetadataVO -> mappedParameters.contains(parameterMetadataVO.getName()));
        return parameterMetadataVOList;
    }


    @Override
    public int delete(long id) {
        return 0;
    }

    @Override
    public List<OntologyFunctionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {
        return null;
    }


}
