package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyGroupBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:00
 */

@Api(tags = "本体所属组别管理")
@RestController
@RequestMapping("/OntologyGroup")
public class OntologyGroupController {

    @Resource
    private OntologyGroupService ontologyGroupService;

    @PostMapping("/add")
    @ApiOperation(value = "新增本体组别")
    public DataResult<Integer> add(@RequestBody OntologyGroupBO ontologyGroupBO) {
        return DataResult.ofData(ontologyGroupService.add(ontologyGroupBO));
    }

    @DeleteMapping("/delete")
    @ApiOperation(value = "删除本体组别")
    public DataResult<Integer> delete(@RequestParam List<Long> ids) {
        return DataResult.ofData(ontologyGroupService.delete(ids));
    }

    @PostMapping("/update")
    @ApiOperation(value = "修改本体组别")
    public DataResult<Integer> update(@RequestBody OntologyGroupBO ontologyGroupBO) {
        return DataResult.ofData(ontologyGroupService.update(ontologyGroupBO));
    }

    @GetMapping("/queryById")
    @ApiOperation(value = "根据组别id查询一个本体组别")
    public DataResult<OntologyGroupVO> getOntologyGroupById(@RequestParam @ApiParam(value = "本体组别id", required = true) Long id) {
        return DataResult.ofData(ontologyGroupService.getOntologyGroupById(id));
    }

}
