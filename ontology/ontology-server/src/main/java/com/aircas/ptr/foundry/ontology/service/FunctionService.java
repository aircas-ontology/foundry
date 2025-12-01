package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface FunctionService extends IService<Function> {


    Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize);

    String executeFunction(FunctionExecuteParam param);


    FunctionDetailVO getFunctionDetailByApi(String api);

    void deleteByApi(String api);


    void createFunction(FunctionCreateParam param);

    void updateFunction(FunctionUpdateParam param);

}
