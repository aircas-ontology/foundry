package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionParamMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class FunctionParamServiceImpl extends ServiceImpl<FunctionParamMapper, FunctionParamPO> implements FunctionParamService {
}
