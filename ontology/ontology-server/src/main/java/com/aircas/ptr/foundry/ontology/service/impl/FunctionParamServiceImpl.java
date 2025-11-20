package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionParamMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class FunctionParamServiceImpl extends ServiceImpl<FunctionParamMapper, FunctionParamPO> implements FunctionParamService {

    @Resource
    private FunctionParamMapper functionParamMapper;

    @Override
    public int insertBatch(List<FunctionParamPO> params) {
        return functionParamMapper.insertBatch(params);
    }
}
