import com.aircas.ptr.foundry.ontology.Xtmb;
import com.aircas.ptr.foundry.ontology.function.Parameter
import com.aircas.ptr.foundry.ontology.application.service.ObjectService
import com.aircas.ptr.foundry.common.util.HttpUtil
import com.alibaba.fastjson.JSONObject;

class getXtmbLonLat {

   def handle(@Parameter(name = "primaryKey", description = "舰船的主键") String primaryKey) {

      def url = 'http://192.168.2.235:18000/latestpoint/get/by_mbbhs?mbbhs=' + primaryKey
      def resp = HttpUtil.doGet(url)
      return JSONObject.parseArray(JSONObject.parseObject(resp).getString("results"), JSONObject.class).get(0)
   }

}
