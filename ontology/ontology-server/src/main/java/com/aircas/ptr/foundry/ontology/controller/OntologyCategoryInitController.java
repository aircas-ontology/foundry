package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryInitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 本体/关系/属性分类体系根节点初始化接口。
 *
 * <p>在 Swagger 上执行一次即可：为全部空间建立对象分类树、关系分类树的“全部”根节点，
 * 为每个本体对象建立属性分类树的“全部”根节点，并把对应 categoryId 回填到
 * ontology_meta / ontology_link_group / ontology_property。</p>
 */
@Tag(name = "本体分类体系初始化")
@RestController
@RequiredArgsConstructor
@RequestMapping("/category/init")
public class OntologyCategoryInitController {

    private final OntologyCategoryInitService ontologyCategoryInitService;

    @PostMapping("/all")
    @Operation(summary = "初始化全部空间的对象/关系/属性分类树根节点（建“全部”根节点并回填categoryId）")
    public RestResult<Map<String, Integer>> initAllCategoryRoots() {
        Map<String, Integer> result = ontologyCategoryInitService.initAllCategoryRoots();
        return RestResult.ofData(result);
    }
}
