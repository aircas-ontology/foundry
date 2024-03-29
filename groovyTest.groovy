import com.aircas.ptr.foundry.ontology.entity.objectType.*;
import com.aircas.ptr.foundry.ontology.function.Ontology


//import Javassist.JC;

class Test {

    String handle(HashMap parameters) {
        System.out.println(parameters)
        def mb = Ontology.getObject("xtmb", "7")
        def name = Ontology.getProperty(mb, "zbxh");
        return name
    }
}