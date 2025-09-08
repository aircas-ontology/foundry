import com.aircas.ptr.foundry.ontology.function.Parameter
import com.alibaba.fastjson.JSONObject
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.common.util.DataSensorUtil
import com.aircas.ptr.foundry.common.util.DateUtils
import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.service.ObjectService
import com.aircas.ptr.foundry.ontology.ArleighBurkeClassDestroyer

class sensorPointTimeWindows {

    def handle(@Parameter(name = "primaryKey", description = "舰船的主键") Integer primaryKey,
               @Parameter(name = "lon", description = "经度") Double lon,
               @Parameter(name = "lat", description = "纬度") Double lat) {

        Date now = new Date();
        long startTime = (long) (now.getTime() / 1000)
        long endTime = (long) (DateUtils.addHour(now, 2).getTime() / 1000)
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        def urlOpt = 'http://192.168.51.29:23412/getOptSensorPointTimeWindows'
        def urlSar = 'http://192.168.51.29:23412/getSARSensorPointTimeWindows'
        def urlElc = 'http://192.168.51.29:23412/getElcSensorPointTimeWindows'
        def arr = DataSensorUtil.getData();
        def res = new ArrayList<>();
        JSONObject.parseObject(arr).getJSONArray("RECORDS").forEach(item -> {
            JSONObject object = (JSONObject) item;
            def tle1 = object.getString("TLE1")
            def tle2 = object.getString("TLE2")
            def typ = object.getString("sensortype");
            def sateID = object.getString("SatelliteID")
            def sateName = object.getString("SatelliteName")
            Map<String, Object> data = new HashMap<>();
            data.put("tle1", tle1);
            data.put("tle2", tle2);
            data.put("startTime", startTime);
            data.put("endTime", endTime);
            data.put("pointlon", lon);
            data.put("pointlat", lat);
            def resp = null
            if (typ.equals("OPT")) {
                data.put("sensor-breadth", object.getFloat("sensor-breadth"));
                data.put("sensor-maxRoll", object.getFloat("sensor-maxRoll"));
                data.put("sensor-minRoll", object.getFloat("sensor-minRoll"));
                data.put("sensor-minPitch", object.getFloat("sensor-minPitch"));
                data.put("sensor-maxPitch", object.getFloat("sensor-maxPitch"));
                data.put("sensor-sunAngle", object.getFloat("sensor-sunAngle"));
                resp = HttpUtil.doPost(urlOpt, data)
            } else if (typ.equals("SAR")) {
                data.put("sensor-breadth", object.getFloat("sensor-breadth"));
                data.put("sensor-maxRoll", object.getFloat("sensor-maxRoll"));
                data.put("sensor-minRoll", object.getFloat("sensor-minRoll"));
                data.put("sensor-minPitch", object.getFloat("sensor-minPitch"));
                data.put("sensor-maxPitch", object.getFloat("sensor-maxPitch"));
                resp = HttpUtil.doPost(urlSar, data)
            } else if (typ.equals("Elc")) {
                data.put("sensor-coneAngle", object.getFloat("sensor-coneAngle"));
                resp = HttpUtil.doPost(urlElc, data)
            }
            if (resp != null && JSONObject.parseObject(resp).getJSONObject("message").getInteger("windowsize") > 0) {
                JSONObject.parseObject(resp).getJSONObject("message").forEach((k, v) -> {
                    if (!"windowsize".equals(k)) {
                        Map map = JSONObject.parseObject((String) v, Map.class);
                        map.put("satelliteId", sateID)
                        map.put("satelliteName", sateName)
                        map.put("tle1", tle1)
                        map .put("tle2", tle2)
                        map.put("sensortype", typ)
                        res.add(map)
                    }
                })
            }
        })
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("zcck", JSONObject.toJSONString(res));
        Map<String, Object> whereMap = new HashMap<>();
        whereMap.put("ID", primaryKey);
        objectService.updateObjectData("cfffcb68-2ac1-4062-bc15-fb2293b29de1", dataMap, whereMap)
        return res
    }

}
