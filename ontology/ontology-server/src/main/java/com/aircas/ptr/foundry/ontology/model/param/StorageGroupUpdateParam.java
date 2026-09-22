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
@Schema(description = "存储分组更新参数")
public class StorageGroupUpdateParam {

    @Schema(name = "id", description = "主键id", example = "1")
    @NotNull(message = "id is null")
    private Integer id;

    @Schema(name = "storageName", description = "存储名称", example = "postgresql_updated")
    @NotBlank(message = "storageName is empty")
    private String storageName;
}
