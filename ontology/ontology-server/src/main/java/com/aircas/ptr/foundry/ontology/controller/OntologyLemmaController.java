package com.aircas.ptr.foundry.ontology.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLemmaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLemmaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyStatisticLemmaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyStatisticLemmaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLemmaTreeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLemmaVO;
import com.aircas.ptr.foundry.ontology.service.OntologyLemmaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = "本体词条")
@RestController
@Validated
@RequestMapping("/lemma")
public class OntologyLemmaController {


    @Resource
    private OntologyLemmaService ontologyLemmaService;


    @PostMapping("")
    @ApiOperation(value = "创建单个本体词条")
    public RestResult<Integer> createLemma(@RequestBody @Valid OntologyLemmaCreateParam param) {
        Integer id = ontologyLemmaService.createLemma(param);
        return RestResult.ofData(id);
    }

    @PutMapping("")
    @ApiOperation(value = "批量更新本体词条")
    public RestResult updateLemma(@RequestBody @Valid List<OntologyLemmaUpdateParam> param) {
        ontologyLemmaService.updateLemma(param);
        return RestResult.success();
    }

    @DeleteMapping("/{lemmaId}")
    @ApiOperation(value = "删除本体词条")
    public RestResult deleteLemma(@PathVariable Integer lemmaId) {
        ontologyLemmaService.deleteLemma(lemmaId);
        return RestResult.success();
    }


    @GetMapping("/ontology")
    @ApiOperation(value = "查询本体词条")
    public RestResult<OntologyLemmaTreeVO> queryLemmaByOntologyId(@RequestParam(name = "ontologyUniqueIdentifier", required = true)
                                                      @ApiParam(name = "ontologyUniqueIdentifier", value = "本体unique identifier", required = true)
                                                      @OntologyIdVerify String ontologyUniqueIdentifier) {
        OntologyLemmaTreeVO res = ontologyLemmaService.queryLemmaByOntologyId(ontologyUniqueIdentifier);
        return RestResult.ofData(res);
    }


    @GetMapping("")
    @ApiOperation(value = "查询本体详情")
    public RestResult<OntologyLemmaVO> queryLemmaById(@RequestParam(name = "lemmaId", required = true)
                                   @ApiParam(name = "lemmaId", value = "本体词条id", required = true) Integer lemmaId) {
        OntologyLemmaVO res = ontologyLemmaService.queryLemmaById(lemmaId);
        return RestResult.ofData(res);
    }

    @PostMapping("/statistic")
    @ApiOperation(value = "创建本体统计词条")
    public RestResult<Integer> createStatisticLemma(@RequestBody @Valid OntologyStatisticLemmaCreateParam param) {
        Integer id = ontologyLemmaService.createStatisticLemma(param);
        return RestResult.ofData(id);
    }

    @PutMapping("/statistic")
    @ApiOperation(value = "更新本体统计词条")
    public RestResult updateStatisticLemma(@RequestBody @Valid OntologyStatisticLemmaUpdateParam param) {
        ontologyLemmaService.updateStatisticLemma(param);
        return RestResult.success();
    }


}
