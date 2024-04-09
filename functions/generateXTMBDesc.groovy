import com.aircas.ptr.foundry.ontology.function.Parameter
import com.aircas.ptr.foundry.ontology.Xtmb


class generateXTMBDesc {

    String handle(@Parameter(name = "primaryKey", description = "舰船的主键") String primaryKey, String ss) {
        if (primaryKey == null) {
            return null
        }
        def xtmb = new Xtmb(primaryKey)
        def mbmc = xtmb.mbmc
        def jcgk = xtmb.jcgk
        def ds = xtmb.ds
        def zbxh = xtmb.zbxh
        def jxh = xtmb.jxh
        def desc = mbmc +
                "所在的机场港口为:" + jcgk +
                ",其吨数为：" + ds  +
                ",其装备型号为:" + zbxh +
                ",其机舷号为:" + jxh
        return desc
    }

}