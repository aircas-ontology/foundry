package com.aircas.ptr.foundry.ontology.engine;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import lombok.Builder;
import lombok.Data;

/**
 * 脚本执行上下文：封装一次脚本执行/校验所需的全部入参。
 * <p>
 * {@code inputJson} 为已按参数定义组装、序列化后的单对象入参（JSON 字符串），
 * 引擎不再感知函数参数表，仅负责把它交给对应语言 runner 执行。
 */
@Data
@Builder
public class ScriptExecutionContext {

    /**
     * 脚本语言，决定路由到哪个 runner
     */
    private FunctionLanguageEnum language;

    /**
     * 函数 api，用于 runner 侧编译缓存键
     */
    private String functionApi;

    /**
     * 版本号
     */
    private String version;

    /**
     * 脚本源码
     */
    private String code;

    /**
     * 单对象入参的 JSON 字符串（handle(input) 的 input）
     */
    private String inputJson;

}
