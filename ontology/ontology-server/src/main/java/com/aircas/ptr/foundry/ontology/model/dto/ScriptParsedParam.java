package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脚本参数解析出的单个参数定义（进程内 AST 解析与 runner /parse 回传的统一承接模型）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptParsedParam {

    /** 参数名 */
    private String name;

    /** 归一化后的参数类型（各语言类型文本经 ScriptParamTypeMapper 映射） */
    private FunctionParamTypeEnum type;

    /** 源码里的原始类型文本（int/str/number/类名等），用于推导 referenceType */
    private String rawType;

    /** 参数方向：INPUT / OUTPUT */
    private FunctionParamCategoryEnum category;

    /** 来源：ANNOTATION-签名/类型声明可靠解析；INFERRED-函数体启发式推断 */
    private String source;
}
