package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "本体知识库查询请求")
public class OntologyKnowledgeQueryParam {

    @NotBlank(message = "question is empty")
    private String question;

    @NotNull(message = "top_k is empty")
    private Integer top_k;
}
