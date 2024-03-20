package com.aircas.ptr.foundry.ontology;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;
import javassist.*;
import javassist.bytecode.AccessFlag;
import org.apache.poi.hssf.record.DVALRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
@Service
public class OntologyClassGenerator {



    public Object generate(String uniqueIdentifier) {
        //首先会查看是否存在该动态类，如果有，则卸载。
        //生成类, 类名为本体的API，继承 ObjectType
        //生成类的属性，属性为本体属性的API，类的属性暂定为string类型吧
        //
             //   List<OntologyPropertyVO> selectByOntologyUniqueIdentifier(String uniqueIdentifier);
        ClassPool pool = ClassPool.getDefault();
        CtClass ct = pool.makeClass("Javassist.JC");
        try {
            //添加属性
            CtClass stringClass = pool.get("java.lang.String");
            CtField f = new CtField(stringClass,"id",ct);
            f.setModifiers(AccessFlag.PUBLIC);
            ct.addField(f);
            //添加构造函数
            ct.addConstructor(CtNewConstructor.make("public JC(String pid){this.id=pid;}",ct));

            ct.writeFile();
            Field[] fields = ct.toClass().getFields();
            System.out.println(fields[0].getName()+fields[0].getType());
        } catch (NotFoundException | CannotCompileException | IOException e) {
            e.printStackTrace();
        }



        return new Object();
    }
}
