package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.service.OverviewService;
import com.aircas.ptr.foundry.ontology.model.vo.OverviewCountVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author wangweigang
 */
@Api(tags = "概览")
@RequestMapping("/overview")
@RestController
public class OntologyOverviewController {

    @Resource
    OverviewService overviewService;

    @ApiOperation("获取概览页面统计数据")
    @GetMapping("/count")
    public RestResult<OverviewCountVo> getCount(){

        OverviewCountVo count = overviewService.getCountNotDel();
        return RestResult.ofData(count);
    }
}
