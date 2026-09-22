package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Schema(description = "CDC 全量同步参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class CdcFullSyncParam {

    @Schema(name = "syncSpace", description = "是否同步本体空间索引 ontology_space，缺省 true")
    private Boolean syncSpace;

    @Schema(name = "syncMeta", description = "是否同步本体元数据索引 ontology_meta，缺省 true")
    private Boolean syncMeta;

    @Schema(name = "syncProperty", description = "是否同步属性索引 ontology_property，缺省 true")
    private Boolean syncProperty;

    @Schema(name = "syncInstance", description = "是否同步数据湖实例索引 ontology_instance，缺省 true")
    private Boolean syncInstance;

    @Schema(name = "ontologyUids", description = "仅同步指定本体（unique_identifier 列表）的实例文档，缺省全部启用本体。仅作用于实例同步")
    private List<String> ontologyUids;
}
