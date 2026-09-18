package com.aircas.ptr.foundry.ontology.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkCategoryVO;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "关系分类体系管理")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/link-category")
public class OntologyLinkCategoryController {

    private final OntologyLinkCategoryService ontologyLinkCategoryService;


    @PostMapping
    @Operation(summary = "创建关系分类体系树")
    public RestResult createLinkCategory(@RequestBody @Valid OntologyLinkCategoryCreateParam param) {
        ontologyLinkCategoryService.createCategory(param);
        return RestResult.success();
    }

    @PutMapping
    @Operation(summary = "修改关系分类名称")
    public RestResult updateLinkCategory(@RequestBody @Valid OntologyLinkCategoryUpdateParam param) {
        ontologyLinkCategoryService.updateCategory(param);
        return RestResult.success();
    }


    @DeleteMapping
    @Operation(summary = "删除关系分类树")
    public RestResult deleteLinkCategory(@RequestBody @Valid OntologyLinkCategoryDeleteParam param) {
        ontologyLinkCategoryService.deleteCategory(param);
        return RestResult.success();
    }


    @GetMapping("/tree")
    @Operation(summary = "查询关系分类体系树")
    public RestResult<OntologyLinkCategoryVO> getLinkCategoryTree(@RequestParam(required = true, name = "spaceId")
                                                                 @Parameter(description = "本体空间id")
                                                                 @SpaceIdVerify Integer spaceId) {
        OntologyLinkCategoryVO res = ontologyLinkCategoryService.getCategoryTree(spaceId);
        return RestResult.ofData(res);
    }
}
