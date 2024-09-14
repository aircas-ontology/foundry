import com.aircas.ptr.foundry.ontology.function.Parameter
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.JSONArray
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.common.util.DateUtils
import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.application.service.ObjectService
import com.aircas.ptr.foundry.ontology.ArleighBurkeClassDestroyer

class updateLocation {

    def handle(@Parameter(name = "mbbh", description = "驱逐舰的目标编号") String mbbh) {

        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        def url = 'http://192.168.9.25:7000/web_ts_api/v1.0/get_mz_ts_zxgjd?is_read_grid_height=0'
        String resp = HttpUtil.doGet(url);
        JSONObject respObj = JSONObject.parseObject(resp);
        if (respObj.getInteger("code") != 0) {
            return respObj.getString("msg")
        }
        String objStr = respObj.getJSONArray("data").stream().filter(item -> ((JSONObject) item).getString("mbbh").equals(mbbh)).findFirst().get().toString()
        JSONObject obj = JSONObject.parseObject(objStr);
        Float lon = obj.getFloat("lon")
        Float lat = obj.getFloat("lat")
        long wzsj = DateUtils.fromString2Timestamp(DateUtils.stdFormat(new Date()).split(" ")[0] + " " + obj.getString("position_time"), "yyyy-MM-dd HH:mm:ss")
        Map<String, Object> dataMap = new HashMap<>()
        dataMap.put("lon", lon)
        dataMap.put("lat", lat)
        dataMap.put("wzsj", wzsj)
        Map<String, Object> whereMap = new HashMap<>()
        whereMap.put("mbbh", mbbh)
        objectService.updateObjectData("cfffcb68-2ac1-4062-bc15-fb2293b29de1", dataMap, whereMap)
        return obj as Object
    }

}
