package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
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
public class OntologySpaceCreateDTO {

    private OntologySpaceDTO ontologySpace;

    private OntologyCategoryCreateParam ontologyCategory;

    /**
     * 空间级函数（按空间内本体 action 引用的 functionApi 去重收集）
     */
    private List<OntologyFunctionDTO> functions;

    private List<OntologyCreateDTO> ontologies;

}
