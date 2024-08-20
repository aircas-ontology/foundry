import com.aircas.ptr.foundry.ontology.Satellite;
import com.aircas.ptr.foundry.ontology.Satellitedb;
import com.aircas.ptr.foundry.ontology.function.Parameter
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.alibaba.fastjson.JSONObject;

class satelliteOrbitmath {

    String handle(@Parameter(name = "primaryKey", description = "卫星的主键") String primaryKey) {

        if (primaryKey == null) {
            return "输入参数错误，卫星id不能为空";
        }
        def url = 'http://192.168.2.235:9101/api/v1/satellite/getTleOrbitData/' + primaryKey
        def resp = HttpUtil.doGet(url)
        String tle1 = JSONObject.parseArray(resp, JSONObject.class).get(0).getString("tle1");
        String tle2 = JSONObject.parseArray(resp, JSONObject.class).get(0).getString("tle2");
        return resp
    }
}
