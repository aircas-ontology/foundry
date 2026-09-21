package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.service.OverviewService;
import com.aircas.ptr.foundry.ontology.model.vo.OverviewCountVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;


@Tag(name = "概览")
@RequestMapping("/overview")
@RestController
public class OntologyOverviewController {

    @Resource
    private OverviewService overviewService;

    @Operation(summary = "获取概览页面统计数据")
    @GetMapping("/count")
    public RestResult<OverviewCountVO> getCount(){
        return RestResult.ofData(overviewService.getCount());
    }
}
