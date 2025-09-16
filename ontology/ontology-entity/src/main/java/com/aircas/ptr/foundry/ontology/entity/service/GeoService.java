package com.aircas.ptr.foundry.ontology.entity.service;

import com.aircas.ptr.foundry.ontology.entity.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 地理位置服务
 */
@Service
public class GeoService {

    @Autowired
    private GeoUtils geoUtils;
    
    /**
     * 计算两个经纬度点之间的距离
     * 
     * @param lat1 第一个点的纬度
     * @param lon1 第一个点的经度
     * @param lat2 第二个点的纬度
     * @param lon2 第二个点的经度
     * @return 两点之间的距离，单位：公里
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        return geoUtils.calculateDistance(lat1, lon1, lat2, lon2);
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
    public boolean isWithinAttackRange(double lat1, double lon1, double lat2, double lon2, double attackRange) {
        return geoUtils.isWithinAttackRange(lat1, lon1, lat2, lon2, attackRange);
    }
} 