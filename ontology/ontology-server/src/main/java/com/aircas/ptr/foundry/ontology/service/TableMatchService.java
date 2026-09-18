package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.TableMatchParam;
import com.aircas.ptr.foundry.ontology.model.vo.TableMatchResultVO;

/**
 * 表智能匹配服务
 * <p>
 * 输入：用户自然语言描述 + 数据源id
 * 输出：LLM 选中的主表 + 通过外键扩展出的关联表，每张表附带完整字段与中文描述
 */
public interface TableMatchService {

    /**
     * 根据用户描述在指定数据源中智能匹配相关表
     */
    TableMatchResultVO predict(TableMatchParam param);
}
