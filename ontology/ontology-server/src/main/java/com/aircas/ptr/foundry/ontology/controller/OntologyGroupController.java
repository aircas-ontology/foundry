package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.GroupIdParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupAddParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
@RequestMapping("/group")
public class OntologyGroupController {

    @Resource
    private OntologyGroupService ontologyGroupService;


    @PostMapping("")
    @ApiOperation(value = "新增本体分组")
    public RestResult create(@RequestBody @Valid OntologyGroupAddParam param) {
        //todo
        return RestResult.success();
    }

    @DeleteMapping("/{groupId}")
    @ApiOperation(value = "删除本体分组")
    public RestResult delete(@PathVariable(required = true,name = "groupId")  String groupId) {
        //todo
        return RestResult.success();
    }

    @PutMapping("")
    @ApiOperation(value = "修改本体分组")
    public RestResult update(@RequestBody @Valid OntologyGroupUpdateParam updateParam) {
        //todo
        return RestResult.success();
    }


    @GetMapping("/search")
    @ApiOperation("关键字检索本体分组")
    public RestResult<List<OntologyGroupVO>> searchOntologyGroups(@RequestParam(required = false,name = "keyword") String keyword) {
        //todo
        return RestResult.success();
    }

    @GetMapping("/link/graph")
    @ApiOperation("查询分组下所有本体的关系(graph)")
    public RestResult<OntologyLinkGraphVO> getOntologyGroupLinks(@RequestParam(required = true , name = "groupId") String groupId) {
        return RestResult.ofData(ontologyGroupService.getOntologyGroupLinks(groupId));
    }


}
