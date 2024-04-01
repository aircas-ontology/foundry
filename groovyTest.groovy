import com.aircas.ptr.foundry.ontology.entity.objectType.*;
import com.aircas.ptr.foundry.ontology.function.Ontology;


class groovyTest {

    String handle(HashMap parameters) {
        System.out.println(parameters)
        String primaryKey = parameters.getOrDefault("primaryKey", null);
        def mbgjd = Ontology.getLinkedObjects("xtmb", primaryKey, "mbgjd");
        print();
        return mbgjd
    }

    void print() {
        System.out.println("test");
    }
}