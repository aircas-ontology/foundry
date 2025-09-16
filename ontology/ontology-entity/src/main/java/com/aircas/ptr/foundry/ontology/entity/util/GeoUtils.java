package com.aircas.ptr.foundry.ontology.entity.util;

import org.springframework.stereotype.Component;

/**
 * 地理位置工具类
 */
@Component
public class GeoUtils {

    private static final double EARTH_RADIUS = 6371.0; // 地球半径，单位：公里

    /**
     * 计算两个经纬度点之间的距离（使用Haversine公式）
     * 
     * @param lat1 第一个点的纬度
     * @param lon1 第一个点的经度
     * @param lat2 第二个点的纬度
     * @param lon2 第二个点的经度
     * @return 两点之间的距离，单位：公里
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // 将经纬度转换为弧度
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);
        
        // Haversine公式
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS * c;
        
        return distance;
    }
    
    /**
     * 判断两个经纬度点之间的距离是否在攻击范围内
     * 
     * @param lat1 第一个点的纬度
     * @param lon1 第一个点的经度
     * @param lat2 第二个点的纬度
     * @param lon2 第二个点的经度
     * @param attackRange 攻击范围，单位：公里
     * @return 如果距离小于等于攻击范围，返回true；否则返回false
     */
    public boolean isWithinAttackRange(double lat1, double lon1, double lat2, double lon2, double attackRange) {
        double distance = calculateDistance(lat1, lon1, lat2, lon2);
        return distance <= attackRange;
    }
} 