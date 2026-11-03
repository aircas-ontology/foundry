package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 「数据源构建对象」向导第④步：在已选空间下，用外部库若干张表的勾选列创建一个对象。
 *
 * <p>选中的表按主子表处理：主表的属性 {@code storageGroup=main}，子表的属性 {@code storageGroup}
 * 用该子表的表名（这也是数据湖里那张实体表的物理表名）。一次只建一个对象。</p>
 *
 * <p>列的原生类型不在请求体里传——服务端会重新连外部库扫一次，向导页的状态可能已经过期，
 * 以现场扫到的 {@code TYPE_NAME} 为准。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "从外部数据源创建对象请求")
public class OntologyBuildFromDatasourceParam extends OntologySpaceIdParam {

    @Schema(name = "categoryId", description = "对象分类 id（ontology_category.id），不传则不挂分类", example = "1")
    private Integer categoryId;

    @Schema(name = "displayName", description = "对象名称（中文展示名）", required = true, example = "舰船")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "apiName", description = "对象 api 名称，同时作为主表在数据湖里的物理表名",
            required = true, example = "ship")
    @NotBlank(message = "apiName is empty")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "对象apiName格式不合法")
    private String apiName;

    @Schema(name = "description", description = "对象描述", example = "我方舰船")
    private String description;

    @Schema(name = "datasourceId", description = "datasource_connection.id，仅用于连外部库复核列与类型",
            required = true, example = "1")
    @NotNull(message = "datasourceId is empty")
    private Integer datasourceId;

    @Schema(name = "tables", description = "勾选的表；其中恰好一张 master=true 作为主表，只选一张表时它即主表",
            required = true)
    @NotEmpty(message = "tables is empty")
    @Valid
    private List<SelectedTable> tables;

    @Schema(name = "foreignKeys", description = "子表 → 主表 的外键列对；每张子表恰好一条，多张表时必填。"
            + "列不要求勾成属性，未勾选的一侧只建物理列（管道列）")
    @Valid
    private List<ForeignKeyParam> foreignKeys;

    @Schema(description = "勾选的一张表")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SelectedTable {

        @Schema(name = "tableName", description = "外部库表名，区分大小写；子表会原样作为实体表名与属性存储分组名",
                required = true, example = "ship_weapon")
        @NotBlank(message = "tableName is empty")
        @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "表名不合法，无法作为属性存储分组")
        private String tableName;

        @Schema(name = "master", description = "是否主表；不传按 false 处理", example = "true")
        private Boolean master;

        @Schema(name = "columns", description = "勾选的列", required = true)
        @NotEmpty(message = "columns is empty")
        @Valid
        private List<SelectedColumn> columns;
    }

    @Schema(description = "勾选的一列")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SelectedColumn {

        @Schema(name = "columnName", description = "外部库列名，同时作为属性 apiName 与实体表列名",
                required = true, example = "ship_name")
        @NotBlank(message = "columnName is empty")
        private String columnName;

        @Schema(name = "displayName", description = "属性展示名；不传时用列注释，列注释也为空时用列名", example = "舰名")
        private String displayName;
    }

    @Schema(description = "子表 → 主表 外键列对")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ForeignKeyParam {

        @Schema(name = "masterTable", description = "主表名", required = true, example = "ship")
        @NotBlank(message = "masterTable is empty")
        private String masterTable;

        @Schema(name = "masterColumn", description = "主表被引用列；可以是主表平台主键 id，不要求勾成属性",
                required = true, example = "ship_id")
        @NotBlank(message = "masterColumn is empty")
        private String masterColumn;

        @Schema(name = "childTable", description = "子表名", required = true, example = "ship_weapon")
        @NotBlank(message = "childTable is empty")
        private String childTable;

        @Schema(name = "childColumn", description = "子表外键列，不要求勾成属性（未勾选时只建物理列）",
                required = true, example = "ship_id")
        @NotBlank(message = "childColumn is empty")
        private String childColumn;
    }
}
