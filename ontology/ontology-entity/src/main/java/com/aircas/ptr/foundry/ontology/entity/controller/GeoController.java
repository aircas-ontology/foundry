package com.aircas.ptr.foundry.ontology.entity.controller;

import com.aircas.ptr.foundry.ontology.entity.service.GeoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 地理位置控制器
 */
@RestController
@RequestMapping("/api/geo")
@Api(tags = "地理位置API")
public class GeoController {

    @Autowired
    private GeoService geoService;
    
    /**
     * 计算两个经纬度点之间的距离
     * 
     * @param lat1 第一个点的纬度
     * @param lon1 第一个点的经度
     * @param lat2 第二个点的纬度
     * @param lon2 第二个点的经度
     * @return 两点之间的距离，单位：公里
     */
    @GetMapping("/distance")
    @ApiOperation("计算两点之间的距离")
    public Map<String, Object> calculateDistance(
            @RequestParam("lat1") double lat1,
            @RequestParam("lon1") double lon1,
            @RequestParam("lat2") double lat2,
            @RequestParam("lon2") double lon2) {
        
        double distance = geoService.calculateDistance(lat1, lon1, lat2, lon2);
        
        Map<String, Object> result = new HashMap<>();
        result.put("distance", distance);
        result.put("unit", "kilometers");
        
        // 使用Java 8兼容的方式创建点1的Map
        Map<String, Object> point1 = new HashMap<>();
        point1.put("latitude", lat1);
        point1.put("longitude", lon1);
        result.put("point1", point1);
        
        // 使用Java 8兼容的方式创建点2的Map
        Map<String, Object> point2 = new HashMap<>();
        point2.put("latitude", lat2);
        point2.put("longitude", lon2);
        result.put("point2", point2);
        
        return result;
    }
    
    /**
     * 判断两个经纬度点之间是否在攻击范围内
     * 
     * @param lat1 第一个点的纬度
     * @param lon1 第一个点的经度
     * @param lat2 第二个点的纬度
     * @param lon2 第二个点的经度
     * @param attackRange 攻击范围，单位：公里
     * @return 如果距离小于等于攻击范围，返回true；否则返回false
     */
    @GetMapping("/attack-range")
    @ApiOperation("判断两点是否在攻击范围内")
    public Map<String, Object> isWithinAttackRange(
            @RequestParam("lat1") double lat1,
            @RequestParam("lon1") double lon1,
            @RequestParam("lat2") double lat2,
            @RequestParam("lon2") double lon2,
            @RequestParam("attackRange") double attackRange) {
        
        boolean isWithin = geoService.isWithinAttackRange(lat1, lon1, lat2, lon2, attackRange);
        double distance = geoService.calculateDistance(lat1, lon1, lat2, lon2);
        
        Map<String, Object> result = new HashMap<>();
        result.put("isWithinAttackRange", isWithin);
        result.put("distance", distance);
        result.put("attackRange", attackRange);
        
        // 使用Java 8兼容的方式创建点1的Map
        Map<String, Object> point1 = new HashMap<>();
        point1.put("latitude", lat1);
        point1.put("longitude", lon1);
        result.put("point1", point1);
        
        // 使用Java 8兼容的方式创建点2的Map
        Map<String, Object> point2 = new HashMap<>();
        point2.put("latitude", lat2);
        point2.put("longitude", lon2);
        result.put("point2", point2);
        
        return result;
    }
} 