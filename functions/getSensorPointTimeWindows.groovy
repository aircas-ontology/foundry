import com.aircas.ptr.foundry.ontology.function.Parameter
import com.alibaba.fastjson.JSONObject
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil
import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.application.service.ObjectService

class getSensorPointTimeWindows {

    def handle(@Parameter(name = "primaryKey", description = "舰船的主键") Integer primaryKey,
               @Parameter(name = "lon", description = "经度") Double lon,
               @Parameter(name = "lat", description = "纬度") Double lat) {
        def shipObjID = "f524f896-536c-481d-8e0a-43ccac5279f3"
        def satelliteObjID = "15660fd7-ffc6-4d22-82fd-694bfb1b5640"
        // 获取ObjectService实例
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        // 构建请求URL和参数
        def url = "http://192.168.9.11:8088/api/satellite/visibility-window"
        Map<String, Object> params = new HashMap<String, Object>() {
            {
                put("line1", "1 33446U 08061A   21319.83888889 0.00000000  33230+1  29779-4 0    03")
                put("line2", "2 33446  97.7929 261.8069 0017746 105.0515  40.5322 14.77171329    07")
                put("latitude", lat.toString())
                put("longitude", lon.toString())
            }
        }
        def resp = HttpUtil.doPostWithForm(url, params)
        if (JSONObject.parseObject(resp).getBoolean("hasVisibility") == true) {
            def startTime = JSONObject.parseObject(resp).getJSONObject("nextWindow").getString("startTime")
            def endTime = JSONObject.parseObject(resp).getJSONObject("nextWindow").getString("endTime")
            def shipOne = objectService.queryObjectByPrimaryKey(shipObjID, primaryKey.toString())
            def nodeCreateUrl = "http://192.168.9.11:8088/api/ontology/nodes"
            //检查舰船节点是否存在
            def urlNodeCheck = "http://192.168.9.11:8088/api/ontology/nodes/" + shipObjID + "-" + primaryKey.toString()
            def respNodeCheck = HttpUtil.doGet(urlNodeCheck)
            if (respNodeCheck == null || respNodeCheck.isEmpty()) {
                println "舰船节点未在线，创建舰船节点"
                // 定义POST请求的URL和数据
                params = new HashMap<String, Object>() {
                    {
                        put("category", "ship")
                        put("createBy", "ontology")
                        put("createTime", "2025-04-01T03:13:24.174Z")
                        put("description", shipOne.getDisplayName())
                        put("id", shipObjID + "-" + primaryKey.toString())
                        put("isDeleted", false)
                        put("key", shipObjID + "-" + primaryKey.toString())
                        put("name", shipOne.getDisplayName())
                        put("properties", {})
                        put("remarks", "导弹驱逐舰")
                        put("source", "xtmb")
                        put("status", "active")
                        put("type", "舰船")
                        put("updateBy", "ontology")
                        put("updateTime", "2025-04-01T03:13:24.174Z")
                        put("version", 1)
                    }
                }
                resp = HttpUtil.doPost(nodeCreateUrl, params)
                println resp.toString()
            }
            //检查卫星节点是否在线
            urlNodeCheck = "http://192.168.9.11:8088/api/ontology/nodes/" + satelliteObjID + "-" + "33446"
            respNodeCheck = HttpUtil.doGet(urlNodeCheck)
            if (respNodeCheck == null || respNodeCheck.isEmpty()) {
                println "卫星节点未在线，创建卫星节点"
                // 定义POST请求的URL和数据
                params = new HashMap<String, Object>() {
                    {
                        put("category", "satellite")
                        put("createBy", "ontology")
                        put("createTime", "2025-04-01T03:13:24.174Z")
                        put("description", "尖兵六号02星")
                        put("id", satelliteObjID + "-" + "33446")
                        put("isDeleted", false)
                        put("key", satelliteObjID + "-" + "33446")
                        put("name", "尖兵六号02星")
                        put("properties", {})
                        put("remarks", "尖兵卫星")
                        put("source", "satellite")
                        put("status", "active")
                        put("type", "卫星")
                        put("updateBy", "ontology")
                        put("updateTime", "2025-04-01T03:13:24.174Z")
                        put("version", 1)
                    }
                }
                resp = HttpUtil.doPost(nodeCreateUrl, params)
                println resp.toString()
            }
            //创建关系
            def relationCreateUrl = "http://192.168.9.11:8088/api/ontology/relations"
            def prop = new HashMap<String, Object>() {
                {
                    put("active-start-time", startTime)
                    put("active-end-time", endTime)
                }
            }
            params = new HashMap<String, Object>() {
                {
                    put("confidence", 1)
                    put("createTime", "2025-04-01T03:13:24.174Z")
                    put("description", "侦察关系")
                    put("from", satelliteObjID + "-" + "33446")
                    put("id", SnowflakeIdUtil.get())
                    put("key", SnowflakeIdUtil.get())
                    put("properties", prop)
                    put("source", "ontology")
                    put("status", "active")
                    put("to", shipObjID + "-" + primaryKey.toString())
                    put("type", "侦察")
                    put("updateTime", "2025-04-01T03:13:24.174Z")
                    put("weight", 0)
                }
            }
            resp = HttpUtil.doPost(relationCreateUrl, params)
            return JSONObject.parseObject(new String(resp.getBytes("UTF-8"), "UTF-8"))
        } else {
            return "不存在侦察窗口"
        }
    }
}
