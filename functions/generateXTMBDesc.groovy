import com.aircas.ptr.foundry.ontology.Xtmb;
import com.aircas.ptr.foundry.ontology.function.Parameter


class generateXTMBDesc {

    String handle(@Parameter(name = "primaryKey", description = "舰船的主键") String primaryKey,
                  @Parameter(name = "onlyName", description = "简短描述，只返回名称") Boolean onlyName) {
        if (primaryKey == null) {
            return null
        }
        def xtmb = new Xtmb(primaryKey)
        def mbmc = xtmb.mbmc
        if (onlyName) return mbmc
        def jcgk = xtmb.jcgk
        def ds = xtmb.ds
        def zbxh = xtmb.zbxh
        def jxh = xtmb.jxh
        def desc = "${mbmc}所在的机场港口为: ${jcgk} \n其吨数为 ${ds} \n其装备型号为: ${zbxh},\n其机舷号为: ${jxh}"
        return desc
    }

}
