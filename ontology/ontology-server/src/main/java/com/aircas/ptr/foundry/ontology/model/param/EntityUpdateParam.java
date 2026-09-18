package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "实体字段更新请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class EntityUpdateParam {

    @NotBlank(message = "schemaName is empty")
    private String schemaName;

    @NotBlank(message = "datasourceId is empty")
    private String datasourceId;

    @NotNull(message = "primaryKeyValue is empty")
    private Object primaryKeyValue;

    @NotEmpty(message = "columnUpdates is empty")
    private List<ColumnUpdate> columnUpdates;

    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ColumnUpdate {

        @NotBlank(message = "propertyUniqIdentifier is empty")
        private String propertyUniqIdentifier;

        @NotBlank(message = "datasourceColumnName is empty")
        private String datasourceColumnName;

        @NotNull(message = "columnValue is null")
        private Object columnValue;
    }


}
