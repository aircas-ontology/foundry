package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.ontology.application.service.OverviewService;
import com.aircas.ptr.foundry.ontology.entity.vo.OverviewCountVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author wangweigang
 */
@Api(tags = "本体相关概览")
@RestController("/overview")
public class OverviewController {

    @Resource
    OverviewService overviewService;

    @ApiOperation("获取概览页面统计数据")
    @GetMapping("/getCount")
    public RestResult<OverviewCountVo> getCount(){
        OverviewCountVo count = overviewService.getCountNotDel();
        return new RestResult<>(ResultCode.SUCCESS, count);
    }
}
