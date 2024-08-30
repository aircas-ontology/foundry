import com.aircas.ptr.foundry.ontology.Xtmb;
import com.aircas.ptr.foundry.ontology.function.Parameter
import com.aircas.ptr.foundry.ontology.application.service.ObjectService
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.alibaba.fastjson.JSONObject;
import com.aircas.ptr.foundry.common.constant.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.OntologyServerApplication;
import com.aircas.ptr.foundry.ontology.repository.param.FilterParam;
import com.aircas.ptr.foundry.ontology.repository.param.QuerySortParam;
import com.github.pagehelper.PageInfo;

class getXtmbLonLat {

   def handle(@Parameter(name = "primaryKey", description = "舰船的主键") String primaryKey) {

      ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class);
      List<FilterParam> filter = new ArrayList<>();
      filter.add(new FilterParam("mbbh", primaryKey));
      List<QuerySortParam> sort = new ArrayList<>();
      sort.add(new QuerySortParam("wzsj", QuerySortEnum.DESC));
      PageInfo<Map<String, Object>> mbgjd = objectService.queryObjectByFilter("mbgjd", filter, 1, 1, sort);
      return mbgjd.getList().get(0);
   }

}
