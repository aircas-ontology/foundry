package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本体格式化构建请求")
public class OntologyBuildQueryParam {

    @NotBlank(message = "query is empty")
    private String query;

    @NotNull(message = "top_k is empty")
    private Integer top_k;
}

