package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.GroupIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupAddParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:00
 */

@Api(tags = "分组")
@RestController
@Validated
@RequestMapping("/group")
public class OntologyGroupController {

    @Resource
    private OntologyGroupService ontologyGroupService;


    @PostMapping("")
    @ApiOperation(value = "新增本体分组")
    public RestResult createGroup(@RequestBody @Valid OntologyGroupAddParam param) {
        ontologyGroupService.createGroup(param);
        return RestResult.success();
    }

    @DeleteMapping("/{groupId}")
    @ApiOperation(value = "删除本体分组")
    public RestResult deleteGroupById(@PathVariable(required = true, name = "groupId") @GroupIdVerify String groupId) {
        ontologyGroupService.deleteGroupById(groupId);
        return RestResult.success();
    }


    @GetMapping("/search")
    @ApiOperation("关键字检索本体分组")
    public RestResult<List<OntologyGroupInfoVO>> searchOntologyGroups(@RequestParam(required = true, name = "keyword") String keyword) {
        return RestResult.ofData(ontologyGroupService.searchByKeyword(keyword));
    }


}
