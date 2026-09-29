package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.ActionCategoryVO;
import com.aircas.ptr.foundry.ontology.service.ActionCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 行为分类体系管理。
 * <p>
 * 接口形态与 {@link OntologyCategoryController} 保持一致（POST 建树 / PUT 改名 / DELETE 删树 / GET 查树），
 * 作用域为「本体空间」，即同一空间共用一棵行为分类体系树。
 */
@Tag(name = "行为分类体系管理")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/action_category")
public class ActionCategoryController {

    private final ActionCategoryService actionCategoryService;


    @PostMapping
    @Operation(summary = "创建行为分类体系树")
    public RestResult createActionCategory(@RequestBody @Valid ActionCategoryCreateParam param) {
        actionCategoryService.createActionCategory(param);
        return RestResult.success();
    }


    @PutMapping
    @Operation(summary = "修改行为分类名称")
    public RestResult updateActionCategory(@RequestBody @Valid ActionCategoryUpdateParam param) {
        actionCategoryService.updateActionCategory(param);
        return RestResult.success();
    }


    @DeleteMapping
    @Operation(summary = "删除行为分类树")
    public RestResult deleteActionCategory(@RequestBody @Valid ActionCategoryDeleteParam param) {
        actionCategoryService.deleteActionCategory(param);
        return RestResult.success();
    }


    @GetMapping("/tree")
    @Operation(summary = "查询行为分类体系树")
    public RestResult<ActionCategoryVO> getActionCategoryTree(@RequestParam(required = true, name = "spaceId")
                                                              @Parameter(description = "本体空间id")
                                                              @SpaceIdVerify Integer spaceId) {
        ActionCategoryVO res = actionCategoryService.getActionCategoryTree(spaceId);
        return RestResult.ofData(res);
    }
}
