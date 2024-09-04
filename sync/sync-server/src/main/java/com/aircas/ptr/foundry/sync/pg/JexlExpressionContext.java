package com.aircas.ptr.foundry.sync.pg;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;

import java.util.ArrayList;
import java.util.List;

public class JexlExpressionContext {


    public static Object expressionEvaluate(List<String> vars, String expressionStr) {

        MapContext context = new MapContext();
        vars.forEach(var -> {
            context.set(var, 10);
        });

        //创建表达式对象
        Expression expression = new JexlEngine().createExpression(expressionStr);
        return expression.evaluate(context);
    }

    public static void main(String[] args) {
        List<String> vars=new ArrayList<>();
        vars.add("a");
        vars.add("b");
        System.out.println("结果:"+JexlExpressionContext.expressionEvaluate(vars,"a+b"));
    }
}
