package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;

import com.aircas.ptr.foundry.ontology.Exception.BaseException;
import com.aircas.ptr.foundry.ontology.Exception.OntologyFunctionParameterPropertyTypeNotSameException;
import com.aircas.ptr.foundry.ontology.application.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.repository.param.ActionHandleParam;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionMappingInBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyActionVO;
import com.aircas.ptr.foundry.ontology.repository.param.ActionAddParam;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;


@Api(tags = "行为")
@RestController
@RequestMapping("/action")
public class OntologyActionController {

    @Resource
    OntologyActionService ontologyActionService;

    @ApiOperation(value = "新增行为")
    @PostMapping("/meta")
    public ApiResult metaSave(@RequestBody ActionAddParam param) throws OntologyFunctionParameterPropertyTypeNotSameException {

        try {
            OntologyActionBo ontologyActionBo = new OntologyActionBo();
            BeanUtils.copyProperties(param, ontologyActionBo);
            if (param.getMappingIns() != null && param.getMappingIns().size() > 0) {
                List<OntologyActionMappingInBO> collect = param.getMappingIns().stream().map(item -> {
                    OntologyActionMappingInBO ontologyActionMappingInBO = new OntologyActionMappingInBO();
                    BeanUtils.copyProperties(item, ontologyActionMappingInBO);
                    return ontologyActionMappingInBO;
                }).collect(Collectors.toList());
                ontologyActionBo.setMappingIns(collect);
            }
            if (param.getObjectPrimaryKeys() != null && param.getObjectPrimaryKeys().size() > 0) {
                String objectKeys = param.getObjectPrimaryKeys().stream().collect(Collectors.joining(","));
                ontologyActionBo.setObjectPrimaryKey(objectKeys);
            }
            return DataResult.ofData(ontologyActionService.save(ontologyActionBo));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "修改行为")
    @PutMapping("/meta")
    public ApiResult metaUpdate(@RequestBody ActionAddParam param) throws OntologyFunctionParameterPropertyTypeNotSameException {

        OntologyActionBo ontologyActionBo = new OntologyActionBo();
        BeanUtils.copyProperties(param, ontologyActionBo);
        ontologyActionBo.setApi(null);
        if (param.getMappingIns() != null && param.getMappingIns().size() > 0) {
            List<OntologyActionMappingInBO> collect = param.getMappingIns().stream().map(item -> {
                OntologyActionMappingInBO ontologyActionMappingInBO = new OntologyActionMappingInBO();
                BeanUtils.copyProperties(item, ontologyActionMappingInBO);
                return ontologyActionMappingInBO;
            }).collect(Collectors.toList());
            ontologyActionBo.setMappingIns(collect);
        }
        if (param.getObjectPrimaryKeys() != null && param.getObjectPrimaryKeys().size() > 0) {
            String objectKeys = param.getObjectPrimaryKeys().stream().collect(Collectors.joining(","));
            ontologyActionBo.setObjectPrimaryKey(objectKeys);
        }
        return DataResult.ofData(ontologyActionService.update(ontologyActionBo));
    }

    @ApiOperation(value = "依据id删除行为")
    @DeleteMapping("/meta/{id}")
    public DataResult<Integer> delete(@PathVariable Long id) {
        return DataResult.ofData(ontologyActionService.delete(id));
    }

    //读取函数列表
    @ApiOperation(value = "根据api获取行为")
    @GetMapping("/meta/{apiName}")
    public ApiResult getMetadataByApi(@PathVariable String apiName) {
        try {
            return DataResult.ofData(ontologyActionService.getMetadataByApi(apiName));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "行为列表")
    @GetMapping("/meta/list")
    public DataResult<PageInfo<OntologyActionVO>> queryMetadataList(@RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "10") Integer size) {

        return DataResult.ofData(ontologyActionService.metaList(page, size));
    }

    @ApiOperation(value = "根据api获取行为参数")
    @GetMapping("/parameter/{apiName}")
    public ApiResult getParameterByApi(@PathVariable String apiName) {
        try {
            return DataResult.ofData(ontologyActionService.getParametersByApi(apiName));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "根据本体id查询行为")
    @GetMapping("/by_ontology")
    public ApiResult queryByOntologyUniqueIdentifier(@RequestParam @ApiParam(value = "本体identifier", required = true) String ontologyUniqueIdentifier) {
        try {
            return DataResult.ofData(ontologyActionService.queryByOntologyUniqueIdentifier(ontologyUniqueIdentifier));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "执行行为")
    @PostMapping("/execute")
    public ApiResult execute(@RequestBody ActionHandleParam param) {
        try {
            return DataResult.ofData(ontologyActionService.handle(param));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }
}
