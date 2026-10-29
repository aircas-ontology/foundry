package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.GlobalSearchParam;
import com.aircas.ptr.foundry.ontology.model.vo.GlobalSearchHitVO;
import com.aircas.ptr.foundry.ontology.service.GlobalSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "全局检索接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Validated
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    /**
     * 全局检索：输入查询内容，默认同时检索五个本体索引
     * （ontology_space / ontology_meta / ontology_property / ontology_instance / ontology_link_group），
     * 传 searchType（1=空间 2=本体对象 3=关系 4=属性 5=实例）只检索对应索引，
     * 匹配文档所有字段，按相关性得分降序返回命中项（名称/类型/描述/空间 id/对象 id/实例 id/属性 id）。
     */
    @PostMapping("/global")
    @Operation(summary = "全局检索（默认跨五个本体索引，可用 searchType 只查空间/本体对象/关系/属性/实例，按相关性排序返回名称/类型/描述及各业务 id）")
    public RestResult<List<GlobalSearchHitVO>> globalSearch(@RequestBody @Validated GlobalSearchParam param) {
        return RestResult.ofData(globalSearchService.search(param));
    }
}
