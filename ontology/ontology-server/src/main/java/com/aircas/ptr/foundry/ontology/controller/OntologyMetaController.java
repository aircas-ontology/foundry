package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.IdentifierVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;


@Api(tags = "元数据")
@RestController
@RequestMapping("/meta")
@Validated
public class OntologyMetaController {

    @Resource
    private OntologyMetaService ontologyMetaService;

//    @PostMapping("")
//    @ApiOperation(value = "创建本体")
//    public RestResult<IdentifierVO> createOntology(@RequestBody @Valid OntologyCreateParam ontologyCreateParam) {
//        String uniqIdentifier = ontologyMetaService.createOntology(ontologyCreateParam);
//        return RestResult.ofData(IdentifierVO.builder().uniqueIdentifier(uniqIdentifier).build());
//    }


    @PostMapping("")
    @ApiOperation(value = "创建本体")
    public RestResult<IdentifierVO> createOntology(@RequestBody @Valid OntologyMetaCreateParam ontologyCreateParam) {
        String uniqIdentifier = ontologyMetaService.createOntology(ontologyCreateParam);
        return RestResult.ofData(IdentifierVO.builder().uniqueIdentifier(uniqIdentifier).build());
    }


    @DeleteMapping("/{ontologyIdentifier}")
    @ApiOperation(value = "删除本体")
    public RestResult deleteOntology(@PathVariable(required = true, name = "ontologyIdentifier") @OntologyIdVerify String ontologyIdentifier) {
        ontologyMetaService.deleteOntology(ontologyIdentifier);
        return RestResult.success();
    }

    @PutMapping("")
    @ApiOperation(value = "修改本体元数据")
    public RestResult updateMeta(@RequestBody @Valid OntologyUpdateParam updateParam) {
        ontologyMetaService.updateMeta(updateParam);
        return RestResult.success();
    }


    @GetMapping("")
    @ApiOperation(value = "根据unique identifier查询一个本体元数据")
    public RestResult<OntologyMetaInfoVO> getMetaByUniqueIdentifier(@RequestParam(name = "uniqueIdentifier", required = true) @ApiParam(name = "uniqueIdentifier", value = "本体unique identifer", required = true) String uniqueIdentifier) {
        return RestResult.ofData(ontologyMetaService.getMetaByUniqueIdentifier(uniqueIdentifier));
    }


    @GetMapping("/search")
    @ApiOperation(value = "搜索本体", notes = "通过关键字匹配本体，包括本体名称、本体描述")
    public RestResult<List<OntologyMetaInfoVO>> searchByKeyword(@RequestParam(name = "keyword", required = true) @ApiParam(name = "keyword", value = "搜索关键词", required = true) String keyword) {
        return RestResult.ofData(ontologyMetaService.searchByKeyword(keyword));
    }

    @GetMapping("/group")
    @ApiOperation(value = "根据groupId查询本体分组")
    public RestResult<List<OntologyGroupMetaVO>> getByGroupId(@RequestParam(name = "groupId", required = false) @ApiParam(name = "groupId", value = "groupId", required = false) String groupId) {
        return RestResult.ofData(ontologyMetaService.getByGroupId(groupId));
    }


    @GetMapping("/group/tree")
    @ApiOperation(value = "根据groupId查询组内本体树")
    public RestResult<List<OntologyMetaNodeVO>> getOntologyTreeByByGroupId(@RequestParam(name = "groupId", required = true) @ApiParam(name = "groupId", value = "groupId", required = true) String groupId) {
        return RestResult.ofData(ontologyMetaService.getOntologyTreeByByGroupId(groupId));
    }

}
