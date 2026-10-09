package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.param.ActionLinkMappingParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "本体行为详情")
public class OntologyActionDetailVO extends OntologyActionInfoVO {

    @Schema(description = "行为关系映射")
    private ActionLinkMappingParam linkMapping;

    @Schema(name = "mappingIns", description = "行为与函数参数映射")
    private List<ActionParamMappingVO> mappingIns;

}
