package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
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
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 「数据源构建对象」向导第④步：在已选空间下，用外部库若干张表的勾选列创建一个对象。
 *
 * <p>选中的表按主子表处理：主表的属性 {@code storageGroup=main}，子表的属性 {@code storageGroup}
 * 用该子表的表名（这也是数据湖里那张实体表的物理表名）。一次只建一个对象。</p>
 *
 * <p>属性类型由请求里的 {@code columns[].dataType} 带入（取值为平台属性类型枚举名，与第③步
 * {@code /table/columns} 返回的 {@code ontologyDataType}、以及属性创建接口的 {@code dataType} 同一套），
 * 请求带齐类型时服务端不连外部库；只有还存在没带 {@code dataType} 的列时才补扫一次，
 * 以现场 {@code TYPE_NAME} 归一化结果补齐。主表的平台主键 {@code id} 例外，固定 {@code Int}。</p>
 *
 * <p>补扫用哪条数据源：{@code datasourceId} 优先，没传则回落到 {@code spaceId} 绑定的那条；
 * 两者都拿不到时无处可扫，未带 {@code dataType} 的列按 {@code String} 落，表与列不再校验存在性。</p>
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

    @Schema(name = "datasourceId", description = "数据源 id（datasource_connection.id），可选："
            + "仅当请求里还有列没带 dataType 时，才用这条数据源连外部库补扫类型与列注释（空间未绑定数据源也能扫）；"
            + "不传则回落到 spaceId 绑定的那条；两者都没有时不扫描，未带 dataType 的列按 String 落", example = "1")
    private Integer datasourceId;

    @Schema(name = "schemaName", description = "外部库 schema 名（向导第②步 /table/list 选定）：需要补扫列类型时用它，"
            + "同时随数据管道消息发给数据组织层做溯源",
            required = true, example = "public")
    @NotBlank(message = "schemaName 必填：外部库 schema 名，与步骤②③选定的一致")
    private String schemaName;

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

        @Schema(name = "sourceTableName", description = "原始表名（溯源用，随数据管道消息发给数据组织层）；不传回退 tableName",
                example = "ship_weapon")
        private String sourceTableName;

        @Schema(name = "master", description = "是否主表；不传按 false 处理", example = "true")
        private Boolean master;

        @Schema(name = "columns", description = "勾选的列，每列建议直接带上第③步返回的 dataType", required = true)
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

        @Schema(name = "dataType", description = "属性数据类型，取平台枚举名：String / Int / Long / Float / Double / "
                + "Decimal / Bool / Date / Timestamp / Short / Byte；与第③步返回的 ontologyDataType 同一个值。"
                + "传了即以它为准、服务端不再为这一列连库；不传则回落到补扫结果，无数据源可扫时按 String 落。"
                + "主表的平台主键 id 例外，固定按 Int 落", example = "String")
        private OntologyDataTypeEnum dataType;

        @Schema(name = "displayName", description = "属性展示名；不传时用列注释，列注释也为空时用列名", example = "舰名")
        private String displayName;

        @Schema(name = "description", description = "属性描述；不传时用外部库列注释，列注释为空时不填",
                example = "舰船中文名称")
        private String description;

        @Schema(name = "sourceColumnName", description = "原始列名（溯源用，随数据管道消息发给数据组织层）；不传回退 columnName",
                example = "ship_name")
        private String sourceColumnName;
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
