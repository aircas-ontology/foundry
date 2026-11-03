package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 「数据源构建对象」第④步的创建结果。
 * <p>所有计数都是本次新建的数量（对象是全新创建的，不存在与历史累计混淆的问题）；
 * 对象已建但实例数据为空——外部库的数据不会自动同步进数据湖，需要另做数据灌入。</p>
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "从外部数据源创建对象的结果")
public class OntologyBuildFromDatasourceResultVO {

    @Schema(name = "uniqueIdentifier", description = "新建对象的 uniqueIdentifier")
    private String uniqueIdentifier;

    @Schema(name = "apiName", description = "对象 api 名称")
    private String apiName;

    @Schema(name = "displayName", description = "对象名称")
    private String displayName;

    @Schema(name = "propertyCount", description = "本次创建的属性数（含平台补的主键 id）")
    private Integer propertyCount;

    @Schema(name = "entityTables", description = "本次在数据湖空间 schema 下建的实体表名，主表在前")
    private List<String> entityTables;

    @Schema(name = "mappingCount", description = "本次写入 table_field_mapping 的主子表列映射行数")
    private Integer mappingCount;

    @Schema(name = "message", description = "可直接展示的提示")
    private String message;
}
