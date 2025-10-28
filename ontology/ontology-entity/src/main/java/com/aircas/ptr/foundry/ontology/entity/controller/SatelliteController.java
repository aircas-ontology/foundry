package com.aircas.ptr.foundry.ontology.entity.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.client.OrbitCalculationAlgorithmClient;
import com.aircas.ptr.foundry.ontology.common.param.SatellitePointParam;
import com.aircas.ptr.foundry.ontology.entity.repository.mapper.main.EntityTableMapper;
import com.aircas.ptr.foundry.ontology.entity.service.SatelliteService;
import com.google.common.collect.Lists;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 卫星控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/satellite")
@Api(tags = "卫星API")
public class SatelliteController {



    @Autowired
    private SatelliteService satelliteService;

    /**
     * todo 仅演示使用
     *
     * @param entityPrimaryKey
     * @return
     */
    @PostMapping("/point/{entityPrimaryKey}")
    @ApiOperation("更新卫星的坐标位置")
    public RestResult updateSatellitePoint(@PathVariable(value = "entityPrimaryKey") Integer entityPrimaryKey) {
        satelliteService.updatePointByPrimaryKey(entityPrimaryKey);
        return RestResult.success();

    }




    /**
     * 计算卫星对指定位置的最近可见窗口
     *
     * @param line1         TLE第一行
     * @param line2         TLE第二行
     * @param latitude      观测点纬度（度）
     * @param longitude     观测点经度（度）
     * @param altitude      观测点海拔高度（米），默认为0
     * @param durationHours 向前计算的时间（小时），默认为24
     * @return 可见窗口信息，如果没有可见窗口则返回hasVisibility=false
     */
    @PostMapping("/visibility-window")
    @ApiOperation("计算卫星可见窗口")
    public Map<String, Object> calculateVisibilityWindow(
            @RequestParam("line1") String line1,
            @RequestParam("line2") String line2,
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(value = "altitude", defaultValue = "0.0") double altitude,
            @RequestParam(value = "durationHours", defaultValue = "24") int durationHours) {

        return satelliteService.calculateVisibilityWindow(line1, line2, latitude, longitude, altitude, durationHours);
    }
} 