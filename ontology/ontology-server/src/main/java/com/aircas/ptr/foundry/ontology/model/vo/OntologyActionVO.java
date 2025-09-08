package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.model.po.OntologyAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
public class OntologyActionVO extends OntologyAction {

    //mapping的列表，为本体和函数所对应的参数
    private List<OntologyActionMappingInVO> mappingIns;

    private String ontologyDisplayName;

    // 对应的函数中排除本体参数属性能对应的所有需要额外输入的参数
    private List<ParameterMetadataVO> parameters;
}
