package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.model.po.*;
import com.aircas.ptr.foundry.ontology.Exception.*;
import com.aircas.ptr.foundry.ontology.application.service.*;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionMappingInBO;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyActionMappingInMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;

import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyActionServiceImpl implements OntologyActionService {

    @Resource
    private final OntologyActionMapper ontologyActionMapper;

    @Resource
    private final OntologyActionMappingInMapper ontologyActionMappingInMapper;

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

    private final static String ONTOLOGY_SELF_IDENTIFIER = "-1";

    private final static String DEFAULT_OBJECT_DESC = "当前本体对象";

    @Override
    public Object handle(String primaryKey, String api)
            throws FunctionClassNotNewInstanceException,
            FunctionFileNotCompiled,
            FunctionRuntimeException,
            FunctionNotFoundException,
            OntologyFunctionNotFoundException,
            OntologyApiNameNotFoundException,
            OntologyFunctionMappedPropertyNotFoundException {

        HashMap<String, Object> parameters = new HashMap<>();
        OntologyActionVO actionVO = getMetadataByApi(api);
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(actionVO.getOntologyUniqueIdentifier());
        if (ontologyMeta == null) {
            throw ExceptionFactory.getOntologyApiNameNotFoundException(null);
        }

        //根据mapping结果，把property注入到parameters
        ObjectOneInfoVO objectOneInfoVO = objectService.queryObjectByPrimaryKey(ontologyMeta.getUniqueIdentifier(), primaryKey);
        List<PropertyValueVO> propertyList = objectOneInfoVO.getProperties();
        Map<String, PropertyValueVO> propertyMap = new HashMap<>();
        propertyList.forEach(propertyValueVO -> propertyMap.put(propertyValueVO.getUniqueIdentifier(), propertyValueVO));

        List<OntologyActionMappingInVO> mappingIns = actionVO.getMappingIns();
        for (OntologyActionMappingInVO mappingIn : mappingIns) {
            String parameterName = mappingIn.getParameterName();
            String propertyUniqueIdentifier = mappingIn.getPropertyUniqueIdentifier();
            // TODO:如果参数是实体本身，需要将实体作为参数传入，现在逻辑还不完善
            if (ONTOLOGY_SELF_IDENTIFIER.equals(propertyUniqueIdentifier)) {
                Map<String, Object> objectMap = new HashMap<>();
                objectMap.put("primaryKey", primaryKey);
                objectMap.put("api", api);
                parameters.put(parameterName, objectMap);
            } else {
                PropertyValueVO propertyValueVO = propertyMap.get(propertyUniqueIdentifier);
                String value = propertyValueVO.getValue();
                parameters.put(parameterName, value);
            }
        }
        //根据property的值，设置参数的值即可，如果是当前对象，则设置为当前对象，也就是currentObject即可，包含api 和primaryKey
        Object result = functionService.handle(actionVO.getFunctionApi(), false, null, parameters);
        log.info(result.toString());
        return result;
    }

    @Override
    public int save(OntologyActionBo ontologyActionBo)
            throws OntologyFunctionParameterPropertyTypeNotSameException, FunctionFileNotCompiled, FunctionNotFoundException,
            FunctionClassNotNewInstanceException, OntologyFunctionBindingParameterNotFoundException, OntologyFunctionMappedPropertyNotFoundException {

        // TODO:暂时不校验
//        checkBindingConsistence(ontologyActionBo);
        OntologyAction ontologyAction = new OntologyAction();
        BeanUtils.copyProperties(ontologyActionBo, ontologyAction);
        Date now = new Date();
        ontologyAction.setCreateTime(now);
        ontologyAction.setUpdateTime(now);
        ontologyAction.setId(SnowflakeIdUtil.get());
        int status = ontologyActionMapper.insert(ontologyAction);
        if (ontologyActionBo.getMappingIns() == null && ontologyActionBo.getMappingIns().size() == 0) {
            return status;
        }
        long id = ontologyAction.getId();
        for (OntologyActionMappingInBO mappingInBO : ontologyActionBo.getMappingIns()) {
            OntologyActionMappingIn mappingIn = new OntologyActionMappingIn();
            BeanUtils.copyProperties(mappingInBO, mappingIn);
            mappingIn.setOntologyActionId(id);
            mappingIn.setCreateTime(now);
            mappingIn.setUpdateTime(now);
            mappingIn.setId(SnowflakeIdUtil.get());
            status = ontologyActionMappingInMapper.insert(mappingIn);
        }
        return status;
    }


    @Override
    public int update(OntologyActionBo ontologyActionBo) {

        OntologyAction ontologyAction = new OntologyAction();
        BeanUtils.copyProperties(ontologyActionBo, ontologyAction);
        int status = ontologyActionMapper.updateByPrimaryKeySelective(ontologyAction);
        if (ontologyActionBo.getMappingIns() == null || ontologyActionBo.getMappingIns().size() == 0) {
            return status;
        }
        for (OntologyActionMappingInBO mappingInBO : ontologyActionBo.getMappingIns()) {
            OntologyActionMappingIn mappingIn = new OntologyActionMappingIn();
            BeanUtils.copyProperties(mappingInBO, mappingIn);
            status = ontologyActionMappingInMapper.updateByPrimaryKeySelective(mappingIn);
        }
        return status;
    }

    @Override
    public boolean handleTask(String api) {

        OntologyAction action = ontologyActionMapper.selectByApi(api);
        String objectPrimaryKeys = action.getObjectPrimaryKey();
        if (StringUtils.isBlank(objectPrimaryKeys)) {
            return false;
        }
        Arrays.stream(objectPrimaryKeys.split(",")).forEach(objectKey -> {
            try {
                handle(objectKey, api);
            } catch (FunctionClassNotNewInstanceException e) {
                throw new RuntimeException(e);
            } catch (FunctionFileNotCompiled e) {
                throw new RuntimeException(e);
            } catch (FunctionRuntimeException e) {
                throw new RuntimeException(e);
            } catch (FunctionNotFoundException e) {
                throw new RuntimeException(e);
            } catch (OntologyFunctionNotFoundException e) {
                throw new RuntimeException(e);
            } catch (OntologyApiNameNotFoundException e) {
                throw new RuntimeException(e);
            } catch (OntologyFunctionMappedPropertyNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        return true;
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

        for (OntologyActionMappingInBO mappingInBO : ontologyFunctionBo.getMappingIns()) {
            String parameterName = mappingInBO.getParameterName();
            String propertyUniqueIdentifier = mappingInBO.getPropertyUniqueIdentifier();
            if (ONTOLOGY_SELF_IDENTIFIER.equals(propertyUniqueIdentifier)) {

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

        OntologyAction ontologyAction = ontologyActionMapper.selectByApi(apiName);
        if (ontologyAction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyAction.getOntologyUniqueIdentifier());
        Map<String, OntologyPropertyVO> ontologyPropertiesMap = queryPropertiesByOntologyUniqueIdentifier(ontologyAction.getOntologyUniqueIdentifier());
        List<OntologyActionMappingIn> allMappings = ontologyActionMappingInMapper.selectByOntologyFunctionId(ontologyAction.getId());
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

        OntologyAction ontologyAction = ontologyActionMapper.selectByApi(functionApi);
        if (ontologyAction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        // TODO: 为什么要函数的参数和行为的参数对比
        List<ParameterMetadataVO> parameterMetadataVOList = functionService.getParameters(ontologyAction.getFunctionApi());
        List<OntologyActionMappingIn> mappingIns = ontologyActionMappingInMapper.selectByOntologyFunctionId(ontologyAction.getId());
        List<String> mappedParameters = mappingIns.stream().map(ontologyActionMappingIn -> ontologyActionMappingIn.getParameterName()).collect(Collectors.toList());
        parameterMetadataVOList.removeIf(parameterMetadataVO -> mappedParameters.contains(parameterMetadataVO.getName()));
        return parameterMetadataVOList;
    }


    @Override
    public List<OntologyActionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier)
            throws OntologyFunctionMappedPropertyNotFoundException, FunctionFileNotCompiled, FunctionNotFoundException, FunctionClassNotNewInstanceException {
        List<OntologyAction> list = ontologyActionMapper.selectByOntologyIdentifier(ontologyUniqueIdentifier);
        List<Long> functionIds = list.stream().map(ontologyAction -> ontologyAction.getId()).collect(Collectors.toList());
        List<OntologyActionMappingIn> allMappings = ontologyActionMappingInMapper.selectByOntologyFunctionIds(functionIds);

        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyUniqueIdentifier);
        Map<String, OntologyPropertyVO> ontologyPropertiesMap = queryPropertiesByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        List<OntologyActionVO> result = new ArrayList();
        for (OntologyAction action : list) {
            OntologyActionVO ontologyFunctionVO = getFunctionVO(action, ontologyMeta, allMappings, ontologyPropertiesMap);
            result.add(ontologyFunctionVO);
        }
        return result;
    }

    private OntologyActionVO getFunctionVO(OntologyAction function,
                                           OntologyMeta ontologyMeta,
                                           List<OntologyActionMappingIn> allMappingIns,
                                           Map<String, OntologyPropertyVO> ontologyPropertiesMap
    ) throws OntologyFunctionMappedPropertyNotFoundException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException {

        OntologyActionVO ontologyActionVO = new OntologyActionVO();
        BeanUtils.copyProperties(function, ontologyActionVO);
        ontologyActionVO.setOntologyDisplayName(ontologyMeta.getDisplayName());

        List<OntologyActionMappingInVO> mappingInVOs = new ArrayList<>();
        String ontologyType = StringUtils.capitalize(ontologyMeta.getApiName());
        for (OntologyActionMappingIn mapping : allMappingIns) {
            boolean isOntologySelf = ONTOLOGY_SELF_IDENTIFIER.equals(mapping.getPropertyUniqueIdentifier());
            OntologyActionMappingInVO mappingInVO = new OntologyActionMappingInVO();
            BeanUtils.copyProperties(mapping, mappingInVO);
            if (!isOntologySelf) {
                OntologyPropertyVO ontologyPropertyVO = ontologyPropertiesMap.get(mapping.getPropertyUniqueIdentifier());
                if (ontologyPropertyVO == null) {
                    throw ExceptionFactory.getOntologyFunctionMappedPropertyNotFoundException(null);
                }
                mappingInVO.setPropertyName(ontologyPropertyVO.getDisplayName());
                mappingInVO.setPropertyType(ontologyPropertyVO.getPropertyType() != null ? ontologyPropertyVO.getPropertyType().name() : null);
            } else {
                mappingInVO.setPropertyName(DEFAULT_OBJECT_DESC);
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
        ontologyActionVO.setMappingIns(mappingInVOs);
        return ontologyActionVO;
    }

    @Override
    public int delete(long id) {

        return ontologyActionMapper.deleteByPrimaryKey(id);
    }

    @Override
    public PageInfo<OntologyActionVO> metaList(Integer page, Integer size) {

        PageHelper.startPage(page, size);
        PageInfo<OntologyAction> pageInfo = new PageInfo<>(ontologyActionMapper.selectAll());
        List<OntologyActionVO> collect = pageInfo.getList().stream().map(item -> {
            OntologyActionVO ontologyActionVO = new OntologyActionVO();
            BeanUtils.copyProperties(item, ontologyActionVO);
            OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyActionVO.getOntologyUniqueIdentifier());
            ontologyActionVO.setOntologyDisplayName(ontologyMeta.getDisplayName());
            return ontologyActionVO;
        }).collect(Collectors.toList());
        PageInfo<OntologyActionVO> pageResult = new PageInfo<>(collect);
        BeanUtils.copyProperties(pageInfo, pageResult);
        pageResult.setList(collect);
        return pageResult;
    }

}
