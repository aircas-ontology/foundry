import com.aircas.ptr.foundry.common.util.HttpUtil
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@TupleConstructor
class TleData {

    String line1

    String line2
}

@TupleConstructor
class SatMesByTleLineParam {

    TleData tleData1

    TleData tleData2

    String startTime

    String endTime

    Double step = 60
}


@Slf4j
class SatelliteDistance {

    ObjectMapper objectMapper = new ObjectMapper();

    FunctionResultVO handle(String satellite1_tle1, String satellite1_tle2, String satellite2_tle1, String satellite2_tle2) {
        var url = "http://192.168.11.107:8088/geo/get2SatMesByTleLine"
        SatMesByTleLineParam param = new SatMesByTleLineParam()
        param.setTleData1(new TleData(satellite1_tle1, satellite1_tle2))
        param.setTleData2(new TleData(satellite2_tle1, satellite2_tle2))

        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusDays(1)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        param.setStartTime(start.format(formatter))
        param.setEndTime(end.format(formatter))
        JsonNode resp = HttpUtil.postJson(url, new HashMap<String, String>(), param, new TypeReference<JsonNode>() {
        })
        //log.info(resp.toString())
        // 获取 data 节点
        JsonNode dataNode = resp.get("data")
        Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
        String firstTimestamp = null
        String lastTimestamp = null
        boolean foundFirst = false

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next()
            String timestamp = entry.getKey()
            JsonNode values = entry.getValue()
            double firstValue = values.get(0).asDouble();
            if (firstValue < 3000000) {
                if (!foundFirst) {
                    firstTimestamp = timestamp;
                    foundFirst = true
                }
                lastTimestamp = timestamp;
            } else {
                if (foundFirst) {
                    break
                }
            }
        }

        if (firstTimestamp != null && lastTimestamp != null) {
            return FunctionResultVO.builder().startTime(firstTimestamp).endTime(lastTimestamp).build()
        }
        return new FunctionResultVO().setStartTime("9999-12-31 00:00:00.000").setEndTime("9999-12-31 00:00:00.000");
    }
}
