package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "表注释VO")
public class TableCommentVO {

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "表注释")
    private String tableComment;
}
