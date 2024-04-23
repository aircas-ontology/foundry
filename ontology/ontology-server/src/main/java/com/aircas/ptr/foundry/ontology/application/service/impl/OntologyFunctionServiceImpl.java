package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.util.BeanUtil;
import com.aircas.ptr.foundry.model.po.OntologyFunction;
import com.aircas.ptr.foundry.model.po.OntologyFunctionMappingIn;
import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.Exception.FunctionClassNotNewInstanceException;
import com.aircas.ptr.foundry.ontology.Exception.FunctionFileNotCompiled;
import com.aircas.ptr.foundry.ontology.Exception.FunctionNotFoundException;
import com.aircas.ptr.foundry.ontology.Exception.FunctionRuntimeException;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyFunctionService;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionRequestBodyBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionMappingInBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyFunctionVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyFunctionMappingInMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyFunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.github.jsonldjava.utils.Obj;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
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
    FunctionService functionService;

    @Override
    public Object handle(FunctionRequestBodyBO functionRequestBodyBO)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException {
        HashMap<String, Object> parameters = functionRequestBodyBO.getParameters();
        OntologyFunctionBo ontologyFunctionBo = queryByOntologyIdentifierAndApi(
                functionRequestBodyBO.getFunctionName(),
                functionRequestBodyBO.getCurrentObject().getApi()
        );
        String originalFunction = ontologyFunctionBo.getOriginalApi();
        //根据mapping结果，把property注入到parameters


        //根据property的值，设置参数的值即可，如果是当前对象，则设置为当前对象，也就是currentObject即可，包含api 和primaryKey

        return functionService.handle(originalFunction,false,null,parameters);
    }

    //实现该function
    private OntologyFunctionBo queryByOntologyIdentifierAndApi(String functionApi, String ontologyApi) {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByApi(ontologyApi);
        if (ontologyMeta == null) {
            return null;
        }
        OntologyFunction ontologyFunction = ontologyFunctionMapper.selectByOntologyIdentifierAndApi(ontologyMeta.getUniqueIdentifier(), functionApi);
        OntologyFunctionBo ontologyFunctionBo = new OntologyFunctionBo();
        if (ontologyFunction != null) {
            //TODO: 如果为空，需要抛异常，function没有找到
            BeanUtils.copyProperties(ontologyFunction, ontologyFunctionBo);
        }
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
    public int delete(long id) {
        return 0;
    }

    @Override
    public List<OntologyFunctionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {
        return null;
    }


}
