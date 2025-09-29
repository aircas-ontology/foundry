package com.aircas.ptr.foundry.ontology.model.view;

import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import lombok.Data;

import java.util.List;

@Data
public class FunctionView {

    private Long functionId;

    private String api;

    private String description;

    private Integer status;

    private String objectTypes;

    private String code;

    private List<FunctionParamPO> functionParams;
}
