import com.aircas.ptr.foundry.ontology.ArleighBurkeClassDestroyer
import com.aircas.ptr.foundry.ontology.function.Parameter

class getShipLocation {

   def handle(@Parameter(name = "primaryKey", description = "驱逐舰的主键") Integer primaryKey) {
      if (primaryKey == null) {
         return null
      }
      def destroyer = new ArleighBurkeClassDestroyer(primaryKey.toString())
      Map<String, Object> data = new HashMap<>();
      data.put("id", destroyer.ID)
      data.put("name", destroyer.Name)
      data.put("lon", destroyer.lon)
      data.put("lat", destroyer.lat)
      data.put("hx", destroyer.hx)
      data.put("hs", destroyer.hs)
      data.put("wxsj", destroyer.wzsj)
      data.put("mbbh", destroyer.mbbh)

      return data
   }

}
