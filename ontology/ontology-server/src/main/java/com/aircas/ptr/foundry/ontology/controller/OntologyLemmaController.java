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
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "本体百科词条")
@RestController
@Validated
@RequestMapping("/lemma")
public class OntologyLemmaController {


    @Resource
    private OntologyLemmaService ontologyLemmaService;


    @PostMapping("")
    @Operation(summary = "创建单个本体词条")
    public RestResult<Integer> createLemma(@RequestBody @Valid OntologyLemmaCreateParam param) {
        Integer id = ontologyLemmaService.createLemma(param);
        return RestResult.ofData(id);
    }

    @PutMapping("")
    @Operation(summary = "批量更新本体词条")
    public RestResult batchUpdateLemma(@RequestBody @Valid List<OntologyLemmaUpdateParam> param) {
        ontologyLemmaService.batchUpdateLemma(param);
        return RestResult.success();
    }

    @DeleteMapping("/{lemmaId}")
    @Operation(summary = "删除本体词条")
    public RestResult deleteLemma(@PathVariable(required = true, name = "lemmaId") Integer lemmaId) {
        ontologyLemmaService.deleteLemma(lemmaId);
        return RestResult.success();
    }


    @GetMapping("/ontology")
    @Operation(summary = "查询本体词条")
    public RestResult<OntologyLemmaTreeVO> queryLemmaByOntologyId(@RequestParam(name = "ontologyUniqueIdentifier", required = true)
                                                      @Parameter(description = "本体unique identifier")
                                                      @OntologyIdVerify String ontologyUniqueIdentifier) {
        OntologyLemmaTreeVO res = ontologyLemmaService.queryLemmaByOntologyId(ontologyUniqueIdentifier);
        return RestResult.ofData(res);
    }


    @GetMapping("")
    @Operation(summary = "查询词条详情")
    public RestResult<OntologyLemmaVO> queryLemmaById(@RequestParam(name = "lemmaId", required = true)  @Parameter(description = "本体词条id") Integer lemmaId) {
        OntologyLemmaVO res = ontologyLemmaService.queryLemmaById(lemmaId);
        return RestResult.ofData(res);
    }

    @PostMapping("/statistic")
    @Operation(summary = "创建本体统计词条(webhook)")
    public RestResult<Integer> createStatisticLemma(@RequestBody @Valid OntologyStatisticLemmaCreateParam param) {
        Integer id = ontologyLemmaService.createStatisticLemma(param);
        return RestResult.ofData(id);
    }

    @PutMapping("/statistic")
    @Operation(summary = "更新本体统计词条(webhook)")
    public RestResult updateStatisticLemma(@RequestBody @Valid OntologyStatisticLemmaUpdateParam param) {
        ontologyLemmaService.updateStatisticLemma(param);
        return RestResult.success();
    }


}
