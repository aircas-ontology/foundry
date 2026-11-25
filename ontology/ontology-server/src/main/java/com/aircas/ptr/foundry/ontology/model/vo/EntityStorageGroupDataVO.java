package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "存储分组数据查询结果")
public class EntityStorageGroupDataVO {

    @Schema(name = "ontologyUniqueIdentifier", description = "对象id", example = "ea512d439cd34c6ebbcde6b692261ab5")
    private String ontologyUniqueIdentifier;

    @Schema(name = "storageGroup", description = "属性存储分组名称", example = "guiji")
    private String storageGroup;

    @Schema(name = "rows", description = "该存储分组的数据行，按请求的排序属性与条数返回")
    private List<StorageGroupRow> rows;

    @Data
    @Builder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "存储分组数据行")
    public static class StorageGroupRow {

        @Schema(name = "entityPrimaryKey", description = "该行所属实体的主键（主存储分组数据即为本行自身的主键）", example = "1")
        private Object entityPrimaryKey;

        @Schema(name = "displayName", description = "所属实体在主存储分组里的展示名（主存储分组标题属性的值，标题属性为空时回落实体主键）", example = "张三")
        private String displayName;

        @Schema(name = "props", description = "该行在存储分组内的属性值")
        private List<EntityPropertyRowDetailVO.PropertyInfo> props;
    }
}
