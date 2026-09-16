package com.aircas.ptr.foundry.ontology.model.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体属性可见性VO")
public class OntologyPropertyPrimaryCategoryVO {

    private String key;

    private Integer value;

    private String displayName;
}
