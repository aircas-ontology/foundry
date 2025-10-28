package com.aircas.ptr.foundry.ontology.client;


import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.common.param.SatellitePointParam;
import com.aircas.ptr.foundry.ontology.common.vo.BaseSatelliteResp;
import com.aircas.ptr.foundry.ontology.common.vo.SatellitePointVO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
public class OrbitCalculationAlgorithmClient {

    private static final String URL_PREFIX = "http://192.168.11.107:8088";

    private static final String GET_SATELLITE_POINT_URL = "/api/v1/satellite/getXYZPointByTLE";

    public BaseSatelliteResp<List<SatellitePointVO>> getSatellitePoint(SatellitePointParam param) {
        String url = URL_PREFIX + GET_SATELLITE_POINT_URL;
        return HttpUtil.postJson(url, new HashMap<>(), param, new TypeReference<BaseSatelliteResp<List<SatellitePointVO>>>() {
        });
    }

}
