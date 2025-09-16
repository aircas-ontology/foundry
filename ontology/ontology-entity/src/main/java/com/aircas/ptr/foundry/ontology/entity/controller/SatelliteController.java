package com.aircas.ptr.foundry.ontology.entity.controller;

import com.aircas.ptr.foundry.ontology.entity.service.SatelliteService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 卫星控制器
 */
@RestController
@RequestMapping("/api/satellite")
@Api(tags = "卫星API")
public class SatelliteController {

    @Autowired
    private SatelliteService satelliteService;

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