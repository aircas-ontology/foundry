import com.aircas.ptr.foundry.ontology.function.Parameter
import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.application.service.ObjectService
import com.aircas.ptr.foundry.common.constant.ActionHandleRuleAddConditionEnum
import com.aircas.ptr.foundry.ontology.repository.param.QuerySortParam
import com.aircas.ptr.foundry.common.constant.QuerySortEnum
import com.aircas.ptr.foundry.ontology.repository.param.FilterParam

class getTrajectory {

    def handle(@Parameter(name = "mbbh", description = "驱逐舰的目标编号") String mbbh,
               @Parameter(name = "start", description = "开始时间") Long start,
               @Parameter(name = "end", description = "结束时间") Long end) {

        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        List<QuerySortParam> sorts = new ArrayList<>()
        sorts.add(new QuerySortParam("wzsj", QuerySortEnum.DESC))
        List<FilterParam> filters = new ArrayList<>()
        filters.add(new FilterParam("mbbh", mbbh, ActionHandleRuleAddConditionEnum.EQ))
        filters.add(new FilterParam("wzsj", start.toString(), ActionHandleRuleAddConditionEnum.GE))
        filters.add(new FilterParam("wzsj", end.toString(), ActionHandleRuleAddConditionEnum.LE))
        List<double[]> collect = new ArrayList<>()
        List<Map<String, Object>> restList = objectService.queryObjectByFilter("mbgjd", filters, sorts)
        return restList
    }
}
