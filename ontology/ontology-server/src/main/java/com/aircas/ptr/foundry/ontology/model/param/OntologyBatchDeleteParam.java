package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "批量删除对象请求参数")
public class OntologyBatchDeleteParam extends OntologySpaceIdParam {

    @Schema(name = "ontologyIdentifiers", description = "对象唯一标识列表", example = "[\"uuid-1\", \"uuid-2\"]", required = true)
    @NotEmpty(message = "ontologyIdentifiers is empty")
    private List<String> ontologyIdentifiers;
}
