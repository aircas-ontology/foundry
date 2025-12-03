import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.common.util.PreconditionUtils
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.google.common.collect.Lists
import groovy.util.logging.Slf4j
import org.apache.commons.collections.CollectionUtils

@Slf4j
class ShipTracePredict {

    FunctionResultVO<JsonNode> handle(List<Double> lat, List<Double> lon, List<Double> timestamp) {
        var url = "http://192.168.9.29:15000/predict"
        if (CollectionUtils.isEmpty(lat) || lat.size() < 3 ||
                CollectionUtils.isEmpty(lon) || lon.size() < 3 ||
                CollectionUtils.isEmpty(timestamp) || timestamp.size() < 3) {
            PreconditionUtils.checkArgument("请求参数长度不能小于3")
        }
        Map<String, Object> param = new HashMap<>()
        List<List<Double>> pos = new ArrayList<>()
        for (int i = 0; i < lat.size(); i++) {
            pos.add(Lists.newArrayList(timestamp[i], lat[i], lon[i]))
        }
        param.put("trace_data", pos)
        JsonNode resp = HttpUtil.postJson(url, new HashMap<String, String>(), param, new TypeReference<JsonNode>() {
        })
        var res = FunctionResultVO.builder().data(resp).build()
        log.info(res.toString())
        return res
    }


}