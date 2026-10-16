package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.ScriptParseParam;
import com.aircas.ptr.foundry.ontology.model.vo.ScriptParseResultVO;

/**
 * 脚本参数解析服务：接收前端传来的脚本源码，后端按语言 AST 解析出输入/输出参数。
 */
public interface ScriptParamParseService {

    /**
     * 解析脚本参数。
     *
     * @param param 语言 + 源码
     * @return 分组后的入参/出参定义；无法解析时 parsed=false 且列表为空
     */
    ScriptParseResultVO parseParams(ScriptParseParam param);
}
