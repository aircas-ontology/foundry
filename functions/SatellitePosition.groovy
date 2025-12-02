import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.ontology.common.vo.BaseSatelliteResp
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO
import com.fasterxml.jackson.core.type.TypeReference
import groovy.transform.TupleConstructor

@TupleConstructor
class J2000Position {

    String time
    Double x
    Double y
    Double z
    Double vx
    Double vy
    Double vz

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

class SatellitePosition {

    FunctionResultVO<J2000Position> handle(String tle1, String tle2) {
        var url = "http://192.168.11.107:8088/api/v1/satellite/getXYZPointByTLE"
        var param = new TleParam(tle1, tle2, "", "", 1, "SGP4")
        BaseSatelliteResp<List<J2000Position>> resp = HttpUtil.postJson(url, new HashMap<String, String>(), param, new TypeReference<BaseSatelliteResp<List<J2000Position>>>() {
        })
        return FunctionResultVO.builder().data(resp.getData().get(0)).build()
    }

}
