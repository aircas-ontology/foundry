package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "存储分组创建参数")
public class StorageGroupCreateParam {

    @Schema(name = "ontologyUniqueIdentifier", description = "本体对象唯一标识", example = "ship_001")
    @NotBlank(message = "ontologyUniqueIdentifier is empty")
    private String ontologyUniqueIdentifier;

    @Schema(name = "storageName", description = "存储名称", example = "postgresql_main")
    @NotBlank(message = "storageName is empty")
    private String storageName;
}
