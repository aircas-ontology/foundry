package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.GroupIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupUpdatedParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;



@Api(tags = "本体分组管理")
@RestController
@Validated
@RequestMapping("/group")
public class OntologyGroupController {

    @Resource
    private OntologyGroupService ontologyGroupService;


    @PostMapping("")
    @ApiOperation(value = "新增本体分组")
    public RestResult createGroup(@RequestBody @Valid OntologyGroupCreateParam param) {
        ontologyGroupService.createGroup(param);
        return RestResult.success();
    }

    @PutMapping("")
    @ApiOperation(value = "编辑本体分组")
    public RestResult updateGroup(@RequestBody @Valid OntologyGroupUpdatedParam param) {
        ontologyGroupService.updateGroup(param);
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
    public RestResult<List<OntologyGroupInfoVO>> searchOntologyGroups(@RequestParam(required = false, name = "keyword") String keyword) {
        return RestResult.ofData(ontologyGroupService.searchByKeyword(keyword));
    }

    @GetMapping
    @ApiOperation("根据groupId查询分组")
    public RestResult<OntologyGroupInfoVO> getGroupById(@RequestParam(required = true, name = "groupId") String groupId) {
        return RestResult.ofData(ontologyGroupService.getGroupById(groupId));
    }


    @GetMapping("/space")
    @ApiOperation("根据空间id查询分组")
    public RestResult<List<OntologyGroupInfoVO>> getGroupBySpaceId(@RequestParam(required = true, name = "spaceId") Integer spaceId) {
        return RestResult.ofData(ontologyGroupService.getGroupBySpaceId(spaceId));
    }

}
