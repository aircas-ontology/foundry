package com.aircas.ptr.foundry.ontology.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyCategoryVO;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Api(tags = "本体分类体系管理")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/category")
public class OntologyCategoryController {

    private final OntologyCategoryService ontologyCategoryService;


    @PostMapping
    @ApiOperation(value = "创建本体分类体系树")
    public RestResult createCategory(@RequestBody @Valid OntologyCategoryCreateParam param) {
        ontologyCategoryService.createCategory(param);
        return RestResult.success();
    }

    @PutMapping
    @ApiOperation(value = "修改本体分类名称")
    public RestResult updateCategory(@RequestBody @Valid OntologyCategoryUpdateParam param) {
        ontologyCategoryService.updateCategory(param);
        return RestResult.success();
    }


    @DeleteMapping
    @ApiOperation(value = "删除本体分类树")
    public RestResult deleteCategory(@RequestBody @Valid OntologyCategoryDeleteParam param) {
        ontologyCategoryService.deleteCategory(param);
        return RestResult.success();
    }


    @GetMapping("/tree")
    @ApiOperation(value = "查询本体分类体系树")
    public RestResult<OntologyCategoryVO> getCategoryTree(@RequestParam(required = true, name = "spaceId")
                                                          @ApiParam(value = "本体空间id", required = true)
                                                          @SpaceIdVerify Integer spaceId) {
        OntologyCategoryVO res = ontologyCategoryService.getCategoryTree(spaceId);
        return RestResult.ofData(res);
    }
}
