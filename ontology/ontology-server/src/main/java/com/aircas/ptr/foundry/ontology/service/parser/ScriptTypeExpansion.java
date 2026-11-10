package com.aircas.ptr.foundry.ontology.service.parser;

import com.aircas.ptr.foundry.ontology.model.dto.ScriptParsedParam;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;

import java.util.List;
import java.util.Set;

/**
 * 脚本内声明类型的字段展开回调。
 * <p>反射只认得工程内类（{@code Class.forName}），脚本里顺手写的
 * {@code class OrbitInfo { Float longitude }} 不在 classpath 上，只有解析器的 AST 认得；
 * 泛型实参指向这类脚本内类时，字段结构必须由解析器自己提供。</p>
 */
interface ScriptTypeExpansion {

    /**
     * 按类名展开业务字段。
     *
     * @param className 类名（简单名或全限定名，已由调用方剥掉集合泛型）
     * @return 展开出的字段列表；不是脚本内类时返回 {@code null}，由调用方回落到工程内类反射展开
     */
    List<ScriptParsedParam> expand(String className, FunctionParamCategoryEnum category, Set<String> visited, int depth);
}
