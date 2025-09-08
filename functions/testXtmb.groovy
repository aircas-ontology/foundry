import com.aircas.ptr.foundry.ontology.Xtmb
import com.aircas.ptr.foundry.ontology.function.Parameter


class testXtmb{

    String handle(@Parameter(name = "xtmb", description = "舰船对象作为参数") Xtmb xtmb) {

        def mbmc = xtmb.mbmc
        def jcgk = xtmb.jcgk
        def ds = xtmb.ds
        def zbxh = xtmb.zbxh
        def jxh = xtmb.jxh
        def desc = "${mbmc}所在的机场港口为: ${jcgk} \n其吨数为 ${ds} \n其装备型号为: ${zbxh},\n其机舷号为: ${jxh}"
        return desc
    }
}
