package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyFunctionDTO {

    private String displayName;


    private String functionApi;

    private String description;

    private FunctionModelEnum model;

    private FunctionTypeEnum type;

    private List<InputParam> inputParams;


    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Accessors(chain = true)
    public static class InputParam {

        private String paramName;

        private String paramType;
    }


}
