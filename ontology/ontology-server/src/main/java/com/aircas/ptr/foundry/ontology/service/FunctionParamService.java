package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface FunctionParamService extends IService<FunctionParamPO> {

    /**
     * 原生saveBatch方法，批量插入时，jsonb字段无法从字符串参数自动转换
     * @param params
     * @return
     */
    int insertBatch(List<FunctionParamPO> params);

}
