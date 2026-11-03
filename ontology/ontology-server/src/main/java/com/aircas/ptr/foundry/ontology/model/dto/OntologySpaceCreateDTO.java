package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryCreateParam;
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
     * 关系分类树（顶层为根节点"全部"），对象关系据此还原所属分类
     */
    private OntologyLinkCategoryCreateParam linkCategory;

    /**
     * 空间下的对象关系（ontology_link_group）
     */
    private List<OntologyLinkDTO> links;

    /**
     * 空间级函数（按空间内本体 action 引用的 functionApi 去重收集）
     */
    private List<OntologyFunctionDTO> functions;

    private List<OntologyCreateDTO> ontologies;

}
