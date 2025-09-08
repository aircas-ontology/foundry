import com.aircas.ptr.foundry.ontology.function.Parameter
import com.aircas.ptr.foundry.common.util.DateUtils
import com.aircas.ptr.foundry.ontology.model.request.FilterParam
import com.aircas.ptr.foundry.common.constant.ActionHandleRuleAddConditionEnum
import com.github.pagehelper.PageInfo
import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.service.ObjectService

class getDestroyerLocation {

    def handle(@Parameter(name = "mbbh", description = "驱逐舰编号") String mbbh) {

        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        List<FilterParam> filters = new ArrayList<>()
        filters.add(new FilterParam("mbbh", mbbh, ActionHandleRuleAddConditionEnum.EQ))
        List<double[]> collect = new ArrayList<>()
        PageInfo<Map<String, Object>> mapPageInfo = objectService.queryObjectByFilter("ArleighBurkeClassDestroyer", filters, 1, 9, null)
        if (mapPageInfo.getList().size() > 0) {
            Map<String, Object> objectMap = mapPageInfo.getList().get(0)
            Map<String, Object> data = new HashMap<>();
            data.put("id", objectMap.get("id") as long)
            data.put("name", objectMap.get("name"))
            data.put("lon", objectMap.get("lon") as float)
            data.put("lat", objectMap.get("lat") as float)
            data.put("hx", objectMap.get("hx") as float)
            data.put("hs", objectMap.get("hs") as float)
            data.put("wxsj", objectMap.get("wzsj") as long)
            data.put("mbbh", objectMap.get("mbbh"))
            return data
        } else {
            return null
        }
    }
}
