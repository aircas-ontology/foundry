package com.aircas.ptr.foundry.ontology.model.dto;


import com.aircas.ptr.foundry.ontology.model.param.ActionLinkMappingParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class ActionContextInfoDTO {


    private List<FunctionParameterVO> functionInputParams;

    private List<ActionParamMappingVO> mappings;

    private Map<String, List<Object>> entityDetailMap;

    /**
     * 来源为「外部输入」的参数取值（参数名 -> 值），只有行为测试接口会传入；定时执行为空
     */
    private Map<String, Object> externalParams;

    private OntologyActionDetailVO actionDetailVO;

    private FunctionDetailVO functionDetailVO;

    private List<OntologyProperty> ontologyProperties;

    private EntityActionExecuteParam entityActionExecuteParam;

    //关系涉及的相关参数
    private ActionLinkMappingParam link;

    private String linkToOntologyUniqueIdentifier;

    private Object linkEntityPrimaryKey;


}
