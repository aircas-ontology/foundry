package com.aircas.ptr.foundry.ontology.common.param;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "实体关系创建请求")
public class EntityRelationCreateParam {

    private String entityTableFrom;

    private String entityTableTo;

    private String relationType;
}
