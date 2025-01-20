import com.aircas.ptr.foundry.ontology.function.Parameter
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.JSONArray;
import com.aircas.ptr.foundry.common.constant.QuerySortEnum
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.common.util.DateUtils
import com.aircas.ptr.foundry.ontology.repository.param.FilterParam
import com.aircas.ptr.foundry.common.constant.ActionHandleRuleAddConditionEnum;
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
        List<double[]> collect = new ArrayList<>()
        PageInfo<Map<String, Object>> mapPageInfo = objectService.queryObjectByFilter("mbgjd", filters, 1, 9, sorts)
        for (int i = 0; i < mapPageInfo.getList().size(); i++) {
            Map<String, Object> objectMap = mapPageInfo.getList().get(i)
            long wzsj = objectMap.get("wzsj") as long
            float lat = objectMap.get("lat") as float
            float lon = objectMap.get("lon") as float
            double[] gj_info = new double[3]
            gj_info[0] = wzsj
            gj_info[1] = lat
            gj_info[2] = lon
            collect.add(gj_info)
        }
        // 查询目指数据
        def url_mz = 'http://192.168.9.25:7000/web_ts_api/v1.0/get_mz_ts_zxgjd?is_read_grid_height=0'
        String resp_mz = HttpUtil.doGet(url_mz);
        JSONObject respObj_mz = JSONObject.parseObject(resp_mz);
        if (respObj_mz.getInteger("code") != 0) {
            return respObj_mz.getString("msg")
        }
        String objStr = respObj_mz.getJSONArray("data").stream().filter(item -> ((JSONObject) item).getString("mbbh").equals(primaryKey)).findFirst().get().toString()
        JSONObject obj = JSONObject.parseObject(objStr);
        float lon = obj.getFloat("lon")
        float lat = obj.getFloat("lat")
        long wzsj = DateUtils.fromString2Timestamp(DateUtils.stdFormat(new Date()).split(" ")[0] + " " + obj.getString("position_time"), "yyyy-MM-dd HH:mm:ss")
        double[] mz_info = new double[3]
        mz_info[0] = wzsj
        mz_info[1] = lat
        mz_info[2] = lon
        collect.add(mz_info)
        double[][] inputArr = (double[][]) collect.toArray()
        Map<String, Object> req = new HashMap<String, Object>()
        req.put("trace_data", inputArr)
        def resp = HttpUtil.doPost(url, req)
        JSONArray res = JSONObject.parseObject(resp).getJSONArray("res");
        List<float[]> arr = new ArrayList<>()
        for (int i = 0; i < res.size(); i++) {
            arr.add(new float[]{res.getJSONArray(i).getFloat(1), res.getJSONArray(i).getFloat(0)});
        }
        float[][] resArr = (float[][]) arr.toArray()
        JSONArray ccw_res = JSONObject.parseObject(resp).getJSONArray("ccw_res");
        arr.clear()
        for (int i = 0; i < ccw_res.size(); i++) {
            arr.add(new float[]{ccw_res.getJSONArray(i).getFloat(1), ccw_res.getJSONArray(i).getFloat(0)});
        }
        float[][] ccw_resArr = (float[][]) arr.toArray()
        JSONArray cw_res = JSONObject.parseObject(resp).getJSONArray("cw_res");
        arr.clear()
        for (int i = 0; i < cw_res.size(); i++) {
            arr.add(new float[]{cw_res.getJSONArray(i).getFloat(1), cw_res.getJSONArray(i).getFloat(0)});
        }
        float[][] cw_resArr = (float[][]) arr.toArray()
        JSONArray circle_res = JSONObject.parseObject(resp).getJSONArray("circle_res");
        arr.clear()
        for (int i = 0; i < circle_res.size(); i++) {
            arr.add(new float[]{circle_res.getJSONArray(i).getFloat(1), circle_res.getJSONArray(i).getFloat(0)});
        }
        float[][] circle_resArr = (float[][]) arr.toArray()
        Map<String, Object> data = new HashMap<String, Object>()
        data.put("res", resArr)
        data.put("ccw_res", ccw_resArr)
        data.put("cw_res", cw_resArr)
        data.put("circle_res", circle_resArr)
        return data
    }
}
