import com.aircas.ptr.foundry.ontology.Xtmb;
import com.aircas.ptr.foundry.ontology.function.Parameter
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.alibaba.fastjson.JSONObject;

class lastedByRange {

    def handle(@Parameter(name = "longitude", description = "经度") Double longitude,
               @Parameter(name = "latitude", description = "纬度") Double latitude,
               @Parameter(name = "radius", description = "半径（km）") Double radius) {

        def url = 'http://192.168.2.235:18000/latestpoint/get/by_coordinateAndRangeKm' +
                '?latitude=' + latitude +
                '&longitude=' + longitude +
                '&radius=1000' + radius
        def resp = HttpUtil.doGet(url)
        return JSONObject.parseObject(resp).getJSONArray("results").toJSONString();
    }

}
