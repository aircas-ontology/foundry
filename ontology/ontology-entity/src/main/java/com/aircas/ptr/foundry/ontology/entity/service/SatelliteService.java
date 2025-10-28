package com.aircas.ptr.foundry.ontology.entity.service;

import com.aircas.ptr.foundry.ontology.client.OrbitCalculationAlgorithmClient;
import com.aircas.ptr.foundry.ontology.common.param.SatellitePointParam;
import com.aircas.ptr.foundry.ontology.entity.repository.mapper.main.EntityTableMapper;
import com.aircas.ptr.foundry.ontology.entity.util.SatelliteUtils;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 卫星服务
 */
@Service
@Slf4j
public class SatelliteService {

    @Autowired
    private SatelliteUtils satelliteUtils;

    @Resource
    private OrbitCalculationAlgorithmClient client;


    @Resource
    private EntityTableMapper tableMapper;

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

    @Scheduled(cron = "0/10 * * * * ?")
    public void updatePoint() {
        log.info("start updatePoint");
        updatePointByPrimaryKey(1);
        log.info("end updatePoint");

    }


    public void updatePointByPrimaryKey(Object entityPrimaryKey) {
        var primaryData = tableMapper.selectByPrimaryKey("tjy_satellite", "id", entityPrimaryKey);

        var tle1 = primaryData.get(0).get("tle1").toString();
        var tle2 = primaryData.get(0).get("tle2").toString();
        var res = client.getSatellitePoint(SatellitePointParam.builder()
                .startTime("")
                .endTime("")
                .tle1(tle1)
                .tle2(tle2)
                .interval(1)
                .type("SGP4")
                .build());
        log.info(res.toString());
        var data = res.getData().get(0);
        Map<String, Object> row = new HashMap<>();
        row.put("x", data.getX());
        row.put("y", data.getY());
        row.put("z", data.getZ());
        row.put("vx", data.getVx());
        row.put("vy", data.getVy());
        row.put("vz", data.getVz());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        LocalDateTime localDateTime = LocalDateTime.parse(data.getTime(), formatter);
        row.put("time", localDateTime);
        row.put("tjy_satellite_id", entityPrimaryKey);

        tableMapper.insertRows("tjy_satellite_tjy_satellite_data_j2000", Lists.newArrayList(row));
    }
}