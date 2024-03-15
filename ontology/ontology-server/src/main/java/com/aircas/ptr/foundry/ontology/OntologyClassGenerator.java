package com.aircas.ptr.foundry.ontology;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.poi.hssf.record.DVALRecord;

import java.util.List;


public class OntologyClassGenerator {



    public Object generate(String uniqueIdentifier) {
        //首先会查看是否存在该动态类，如果有，则卸载。
        //生成类, 类名为本体的API，继承 ObjectType
        //生成类的属性，属性为本体属性的API，类的属性暂定为string类型吧
        //
             //   List<OntologyPropertyVO> selectByOntologyUniqueIdentifier(String uniqueIdentifier);
        ClassPool pool = ClassPool.getDefault();
        CtClass ct = pool.makeAnnotation("top.ss007.GenerateClass");
        ct.setInterfaces(new CtClass[]{pool.makeInterface("java.lang.Cloneable")});
        return new Object();
    }
}
