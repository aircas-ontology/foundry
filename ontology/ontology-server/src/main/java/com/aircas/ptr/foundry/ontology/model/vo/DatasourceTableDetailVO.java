package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 外部数据源表结构明细（向导「选择属性」步骤用）：列清单 + 主键 + 与其他勾选表之间的外键候选。
 * <p>只描述外部库的真实结构，不做任何对象侧的改写；第④步进库时的类型归一化以服务端重新扫描的结果为准。
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部数据源表结构明细")
public class DatasourceTableDetailVO {

    @Schema(name = "tableName", description = "表名")
    private String tableName;

    @Schema(name = "tableComment", description = "表注释")
    private String tableComment;

    @Schema(name = "primaryKeyColumns", description = "主键列名，按数据库声明顺序；无主键时为空集合")
    private List<String> primaryKeyColumns;

    @Schema(name = "columns", description = "列清单，按 ORDINAL_POSITION 升序")
    private List<Column> columns;

    @Schema(name = "foreignKeys", description = "本次勾选表集合内部指向主表的外键；每张子表至多一条")
    private List<ForeignKey> foreignKeys;

    @Data
    @Builder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "外部表列信息")
    public static class Column {

        @Schema(name = "columnName", description = "列名")
        private String columnName;

        @Schema(name = "nativeType", description = "数据库原生类型，如 BIGINT / varchar(255)")
        private String nativeType;

        @Schema(name = "description", description = "列注释")
        private String description;

        @Schema(name = "isPrimaryKey", description = "是否主键列")
        private Boolean isPrimaryKey;

        @Schema(name = "ontologyDataType", description = "归一化后的对象属性类型，与第④步进库结果一致")
        private String ontologyDataType;
    }

    @Data
    @Builder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "子表 → 主表 外键候选")
    public static class ForeignKey {

        @Schema(name = "masterTable", description = "主表名")
        private String masterTable;

        @Schema(name = "masterColumn", description = "主表被引用列")
        private String masterColumn;

        @Schema(name = "childTable", description = "子表名")
        private String childTable;

        @Schema(name = "childColumn", description = "子表外键列")
        private String childColumn;

        @Schema(name = "origin", description = "DECLARED 数据库声明的外键；HEURISTIC 同名列推断，需用户确认")
        private ForeignKeyOrigin origin;
    }

    /**
     * 外键来源。向导页据此决定「外键关联（子表 → 主表）」是直接展示还是标黄要求确认。
     */
    public enum ForeignKeyOrigin {
        DECLARED,
        HEURISTIC
    }
}
