import com.aircas.ptr.foundry.ontology.entity.objectType.*;
import com.aircas.ptr.foundry.Javassit.*
import javassist.CtClass;
//import Javassist.JC;

class Test {

    String handle(HashMap parameters) {
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.get("Javassist.JC");
        JC jc = (JC)CtClass.newInstance();
        return 3;
//        System.out.println(ctClass.toString());
//        JC jc = new JC("1");
//        System.out.println(jc.toString());
//        XTMB xtmb = new XTMB();
//        xtmb.mbbh = "3332"
//        return xtmb.mbbh;
    }

    String handle(JC xtmb) {

    }
}