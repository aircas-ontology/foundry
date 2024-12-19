import com.aircas.ptr.foundry.ontology.function.Parameter
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.JSONArray;
import com.aircas.ptr.foundry.common.constant.QuerySortEnum
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.ontology.repository.param.FilterParam
import com.github.pagehelper.PageInfo
import com.aircas.ptr.foundry.ontology.repository.param.QuerySortParam
import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.application.service.ObjectService
import com.aircas.ptr.foundry.ontology.Destroyer

import java.util.stream.Collectors

class trajectoryPredict {

    def handle(@Parameter(name = "primaryKey", description = "舰船的主键") String primaryKey) {

        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        def url = 'http://192.168.9.11:15000/predict'
        List<QuerySortParam> sorts = new ArrayList<>()
        sorts.add(new QuerySortParam("wzsj", QuerySortEnum.DESC))
        List<FilterParam> filters = new ArrayList<>()
        filters.add(new FilterParam("mbbh", primaryKey, ActionHandleRuleAddConditionEnum.EQ))
        PageInfo<Map<String, Object>> mapPageInfo = objectService.queryObjectByFilter("mbgjd", filters, 1, 10, sorts)
        List<float[]> collect = new ArrayList<>()
        float latStart = 0
        float lonStart = 0
        for (int i = 0; i < mapPageInfo.getList().size(); i++) {
            Map<String, Object> objectMap = mapPageInfo.getList().get(i)
            long wzsj = (long) objectMap.get("wzsj")
            float lat = (float) objectMap.get("lat")
            float lon = (float) objectMap.get("lon")
            float hx = (float) objectMap.get("hx")
            float hs = (float) objectMap.get("hs")
            Date now = new Date(wzsj)
            float[] floats = new float[13]
            floats[0] = wzsj
            floats[1] = lat
            floats[2] = lon
            floats[3] = hx
            floats[4] = hs
            floats[7] = now.getMonth()
            floats[8] = now.getDate()
            floats[9] = now.getHours()
            if (i == 0) {
                latStart = lat
                lonStart = lon
            }
            floats[10] = latStart
            floats[11] = lonStart
            collect.add(floats)
        }
        float[][] inputArr = (float[][]) collect.toArray()
        Map<String, Object> data = new HashMap<String, Object>()
        data.put("trace_data", inputArr)
        def resp = HttpUtil.doPost(url, data)
        JSONArray res = JSONObject.parseObject(resp).getJSONArray("res");
        List<float[]> arr = new ArrayList<>()
        for (int i = 0; i < res.size(); i++) {
            arr.add(new float[]{res.getJSONArray(i).getFloat(1), res.getJSONArray(i).getFloat(0)});
        }
        float[][] array = (float[][]) arr.toArray()
        return array
    }
}
