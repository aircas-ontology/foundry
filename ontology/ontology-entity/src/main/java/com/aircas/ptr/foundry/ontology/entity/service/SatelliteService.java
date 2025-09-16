package com.aircas.ptr.foundry.ontology.entity.service;

import com.aircas.ptr.foundry.ontology.entity.util.SatelliteUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 卫星服务
 */
@Service
public class SatelliteService {

    @Autowired
    private SatelliteUtils satelliteUtils;

    /**
     * 计算卫星对指定位置的最近可见窗口
     *
     * @param line1         TLE第一行
     * @param line2         TLE第二行
     * @param latitude      观测点纬度（度）
     * @param longitude     观测点经度（度）
     * @param altitude      观测点海拔高度（米）
     * @param durationHours 向前计算的时间（小时）
     * @return 可见窗口信息，如果没有可见窗口则返回hasVisibility=false
     */
    public Map<String, Object> calculateVisibilityWindow(String line1, String line2,
                                                         double latitude, double longitude,
                                                         double altitude, int durationHours) {
        return satelliteUtils.calculateVisibilityWindow(line1, line2, latitude, longitude, altitude, durationHours);
    }
} 