import com.aircas.ptr.foundry.ontology.function.Parameter
import com.alibaba.fastjson.JSONObject
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.application.service.ObjectService

class getSensorPointTimeWindows {

    def handle(@Parameter(name = "primaryKey", description = "舰船的主键") Integer primaryKey,
               @Parameter(name = "lon", description = "经度") Float lon,
               @Parameter(name = "lat", description = "纬度") Float lat) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        def urlOpt = 'http://192.168.51.29:23412/getOptSensorPointTimeWindows'
        def urlSar = 'http://192.168.51.29:23412/getSARSensorPointTimeWindows'
        def urlElc = 'http://192.168.51.29:23412/getElcSensorPointTimeWindows'
        def arr = "{\n" +
                "  \"RECORDS\": [\n" +
                "    {\n" +
                "      \"SatelliteID\": \"33446\",\n" +
                "      \"SatelliteName\": \"尖兵六号02星\",\n" +
                "      \"TLE1\": \"1 33446U 08061A   21319.83888889 0.00000000  33230+1  29779-4 0    03\",\n" +
                "      \"TLE2\": \"2 33446  97.7929 261.8069 0017746 105.0515  40.5322 14.77171329    07\",\n" +
                "      \"sensortype\": \"OPT\",\n" +
                "      \"sensor-breadth\": \"60000\",\n" +
                "      \"sensor-maxRoll\": \"45\",\n" +
                "      \"sensor-minRoll\": \"-45\",\n" +
                "      \"sensor-maxPitch\": \"0\",\n" +
                "      \"sensor-minPitch\": \"0\",\n" +
                "      \"sensor-sunAngle\": \"10\",\n" +
                "      \"sensor-coneAngle\": \"20\"\n" +
                "    }\n" +
                "  ]\n" +
                "}"
        Map<String, Object> res = new HashMap<>();
        JSONObject.parseObject(arr).getJSONArray("RECORDS").forEach(item -> {
            JSONObject object = (JSONObject) item;
            Map<String, Object> data = new HashMap<>();
            String typ = object.getString("sensortype");
            def sateID = object.getString("SatelliteID")
            if (typ.equals("OPT")) {
                data.put("tle1", object.getString("TLE1"));
                data.put("tle2", object.getString("TLE2"));
                data.put("startTime", 1725552000);
                data.put("endTime", 1725555600);
                data.put("sensor-breadth", object.getFloat("sensor-breadth"));
                data.put("sensor-maxRoll", object.getFloat("sensor-maxRoll"));
                data.put("sensor-minRoll", object.getFloat("sensor-minRoll"));
                data.put("sensor-minPitch", object.getFloat("sensor-minPitch"));
                data.put("sensor-maxPitch", object.getFloat("sensor-maxPitch"));
                data.put("sensor-sunAngle", object.getFloat("sensor-sunAngle"));
                data.put("pointlon", -57.19);
                data.put("pointlat", -27.21);
                def resp = HttpUtil.doPost(urlOpt, data)
                if (JSONObject.parseObject(resp).getJSONObject("message").getInteger("windowsize") > 0) {
                    res.put(sateID, JSONObject.parseObject(resp).getJSONObject("message"))
                }
            } else if (typ.equals("SAR")) {
                data.put("tle1", object.getString("TLE1"));
                data.put("tle2", object.getString("TLE2"));
                data.put("startTime", 1725552000);
                data.put("endTime", 1725555600);
                data.put("sensor-breadth", object.getFloat("sensor-breadth"));
                data.put("sensor-maxRoll", object.getFloat("sensor-maxRoll"));
                data.put("sensor-minRoll", object.getFloat("sensor-minRoll"));
                data.put("sensor-minPitch", object.getFloat("sensor-minPitch"));
                data.put("sensor-maxPitch", object.getFloat("sensor-maxPitch"));
                data.put("pointlon", -57.19);
                data.put("pointlat", -27.21);
                def resp = HttpUtil.doPost(urlSar, data)
                if (JSONObject.parseObject(resp).getJSONObject("message").getInteger("windowsize") > 0) {
                    res.put(sateID, JSONObject.parseObject(resp).getJSONObject("message"))
                }
            } else if (typ.equals("Elc")) {
                data.put("tle1", object.getString("TLE1"));
                data.put("tle2", object.getString("TLE2"));
                data.put("startTime", 1725552000);
                data.put("endTime", 1725555600);
                data.put("sensor-coneAngle", object.getFloat("sensor-coneAngle"));
                data.put("pointlon", -57.19);
                data.put("pointlat", -27.21);
                def resp = HttpUtil.doPost(urlElc, data)
                if (JSONObject.parseObject(resp).getJSONObject("message").getInteger("windowsize") > 0) {
                    res.put(sateID, JSONObject.parseObject(resp).getJSONObject("message"))
                }
            }
        })
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("zcck", JSONObject.toJSONString(res));
        Map<String, Object> whereMap = new HashMap<>();
        whereMap.put("GUID", "hsfw-dataship-0000000000001032");
        objectService.updateObjectData("f524f896-536c-481d-8e0a-43ccac5279f3", dataMap, whereMap);
        return res
    }
}
