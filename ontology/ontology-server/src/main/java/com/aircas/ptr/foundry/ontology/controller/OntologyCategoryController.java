package com.aircas.ptr.foundry.ontology.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyCategoryVO;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "对象分类体系管理")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/category")
public class OntologyCategoryController {

    private final OntologyCategoryService ontologyCategoryService;


    @PostMapping
    @Operation(summary = "创建对象分类体系树")
    public RestResult createCategory(@RequestBody @Valid OntologyCategoryCreateParam param) {
        ontologyCategoryService.createCategory(param);
        return RestResult.success();
    }

    @PutMapping
    @Operation(summary = "修改对象分类名称")
    public RestResult updateCategory(@RequestBody @Valid OntologyCategoryUpdateParam param) {
        ontologyCategoryService.updateCategory(param);
        return RestResult.success();
    }


    @DeleteMapping
    @Operation(summary = "删除对象分类树")
    public RestResult deleteCategory(@RequestBody @Valid OntologyCategoryDeleteParam param) {
        ontologyCategoryService.deleteCategory(param);
        return RestResult.success();
    }


    @GetMapping("/tree")
    @Operation(summary = "查询对象分类体系树")
    public RestResult<OntologyCategoryVO> getCategoryTree(@RequestParam(required = true, name = "spaceId")
                                                          @Parameter(description = "对象空间id")
                                                          @SpaceIdVerify Integer spaceId) {
        OntologyCategoryVO res = ontologyCategoryService.getCategoryTree(spaceId);
        return RestResult.ofData(res);
    }
}
