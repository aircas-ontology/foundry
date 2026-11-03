package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "属性自动关联数据源结果")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutoBindDatasourceResultVO {

    @Schema(name = "boundCount", description = "本次新关联数据源的属性数")
    private int boundCount;

    @Schema(name = "totalBoundCount", description = "本体下已关联数据源的属性总数（含本次新关联）")
    private int totalBoundCount;

    @Schema(name = "blockedItems", description = "未能关联数据源的属性明细")
    private List<BlockedItem> blockedItems;

    @Schema(name = "message", description = "结果提示，可直接展示")
    private String message;

    @Schema(description = "未能关联数据源的属性")
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BlockedItem {

        @Schema(name = "propertyApiName", description = "属性apiName")
        private String propertyApiName;

        @Schema(name = "propertyDisplayName", description = "属性显示名")
        private String propertyDisplayName;

        @Schema(name = "storageGroup", description = "属性存储分组")
        private String storageGroup;

        @Schema(name = "tableName", description = "目标实体表名")
        private String tableName;

        @Schema(name = "columnName", description = "目标列名")
        private String columnName;

        @Schema(name = "actualColumnType", description = "列在实体表中的实际类型，列不存在时为空")
        private String actualColumnType;

        @Schema(name = "expectedColumnType", description = "属性映射出的列类型，属性类型不支持映射时为空")
        private String expectedColumnType;

        @Schema(name = "reason", description = "未关联原因")
        private String reason;
    }
}
