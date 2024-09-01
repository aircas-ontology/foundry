import com.aircas.ptr.foundry.ontology.Xtmb;
import com.aircas.ptr.foundry.ontology.function.Parameter
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.alibaba.fastjson.JSONObject;

class lastedByRange {

    def handle(@Parameter(name = "longitude", description = "经度") Float longitude,
               @Parameter(name = "latitude", description = "纬度") Float latitude,
               @Parameter(name = "radius", description = "半径（km）") Float radius,
               @Parameter(name = "starttime", description = "开始时间") Long starttime,
               @Parameter(name = "endtime", description = "结束时间") Long endtime) {

        def url = 'http://192.168.2.235:18000/latestpoint/get/by_coordinateAndRangeKm' +
                '?endtime=' + endtime +
                '&latitude=' + latitude +
                '&longitude=' + longitude +
                '&radius=1000' + radius +
                '&starttime=' + starttime
        def resp = HttpUtil.doGet(url)
        return JSONObject.parseObject(resp).getJSONArray("results").toJSONString();
    }

}
