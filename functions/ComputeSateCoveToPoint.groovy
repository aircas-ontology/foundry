import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.ontology.common.vo.BaseSatelliteResp
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO
import com.fasterxml.jackson.core.type.TypeReference
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import org.apache.commons.collections.CollectionUtils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@TupleConstructor
class J2000Position {

    String time
    Double vx
    Double vy
    Double vz
    Double x
    Double y
    Double z
}

@TupleConstructor
class Sensor {

    String enum_sensor_type = "0"
    SensorPointing sensor_pointing = new SensorPointing()
}

@TupleConstructor
class SensorPointing {
    Double azimuth = 0
    Double elevation = 90
}

@TupleConstructor
class Target {
    Double alt = 0
    Double lat
    Double lon
}

@TupleConstructor
class ComputeSateCoveToPointRec {

    Double dMaxRange = 0;

    List<J2000Position> satOrbitDataList;

    Sensor sensorDef = new Sensor()

    Target target
}



@TupleConstructor
class TleParam {
    String tle1
    String tle2
    String startTime
    String endTime
    Double interval
    String type
}


@TupleConstructor
class ComputeSateCoveToPointVO {
    String beginTime
    String endTime
    String topTime
}

@Slf4j
class ComputeSateCoveToPoint {


    FunctionResultVO<List<ComputeSateCoveToPointVO>> handle(String tle1, String tle2, Double targetLon ,Double targetLat ) {

        var url = "http://192.168.11.107:8088/api/v1/satellite/getXYZPointByTLE"
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusDays(1)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        var param = new TleParam(tle1, tle2, start.format(formatter), end.format(formatter), 30, "SGP4")
        BaseSatelliteResp<List<J2000Position>> resp = HttpUtil.postJson(url, new HashMap<String, String>(), param, new TypeReference<BaseSatelliteResp<List<J2000Position>>>() {
        })
        List<J2000Position> positions = resp.data
        url = "http://192.168.11.107:8088/api/v1/satellite/computeSateCoveToPoint"
        var req = new ComputeSateCoveToPointRec()
        req.setSatOrbitDataList(positions)
        req.setTarget(new Target(0, targetLat, targetLon))
        var result = HttpUtil.postJson(url, new HashMap<String, String>(), req, new TypeReference<BaseSatelliteResp<List<ComputeSateCoveToPointVO>>>() {
        })
        var vos = result.getData()
        if (CollectionUtils.isEmpty(vos)) {
            return FunctionResultVO.builder().data(vos).build();
        } else {
            var vo = vos.get(0);
            return FunctionResultVO.builder().startTime(vo.beginTime).endTime(vo.endTime).data(vos).build()
        }
    }

}