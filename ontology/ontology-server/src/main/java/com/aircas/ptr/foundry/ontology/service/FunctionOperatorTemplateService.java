package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.param.FunctionOperatorTemplateParam;
import com.aircas.ptr.foundry.ontology.model.po.FunctionOperatorTemplate;

import java.util.List;

/**
 * 函数算子代码模板服务：维护各脚本语言的示例代码，供创建算子时选用。
 */
public interface FunctionOperatorTemplateService {

    /** 查询模板列表；name 非空按名称模糊匹配，language 非空按语言过滤，两者都不传查全部 */
    List<FunctionOperatorTemplate> listTemplates(String name, FunctionLanguageEnum language);

    /** 创建模板；ID 与时间字段由后台生成 */
    FunctionOperatorTemplate create(FunctionOperatorTemplateParam param);

    /** 更新模板；ID 由调用方指定，时间字段由后台生成 */
    FunctionOperatorTemplate update(Long id, FunctionOperatorTemplateParam param);

    /** 删除模板 */
    void delete(Long id);
}
