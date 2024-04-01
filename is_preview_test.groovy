import com.aircas.ptr.foundry.ontology.function.Ontology


class Test {

    //用简单的语言描述一个舰船
    String handle(HashMap parameters) {
        System.out.println(parameters)
        String primaryKey = parameters.getOrDefault("primaryKey", null);
        def xtmb = Ontology.getObject("xtmb", primaryKey)
        def mbmc = Ontology.getTitle(xtmb);
        def jcgk = Ontology.getProperty(xtmb, "jcgk");
        def ds = Ontology.getProperty(xtmb, "ds");
        def zbxh = Ontology.getProperty(xtmb, "zbxh");
        def jxh = Ontology.getProperty(xtmb, "jxh");
        return mbmc + "所在的机场港口为:" + jcgk +
                      ",其吨数为：" + ds  +
                      ",其装备型号为:" + zbxh +
                      ",其机舷号为:" + jxh
    }
}