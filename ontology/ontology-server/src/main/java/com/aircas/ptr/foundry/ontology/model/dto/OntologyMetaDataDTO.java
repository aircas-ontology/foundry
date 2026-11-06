package com.aircas.ptr.foundry.ontology.model.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Set;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyMetaDataDTO {

    private String apiName;

    private String ontologySpaceName;

    private String description;

    private String displayName;

    private Set<String> groupNames;

    private String categoryPath;

    /**
     * 父对象显示名称（继承层级树），导入时据此解析新空间中的父对象 uniqueIdentifier
     */
    private String parentOntologyDisplayName;
}
