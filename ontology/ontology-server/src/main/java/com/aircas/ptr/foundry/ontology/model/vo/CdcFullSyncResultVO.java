package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "CDC 全量同步结果")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CdcFullSyncResultVO {

    @Schema(name = "spaceSynced", description = "本体空间索引写入文档数")
    private long spaceSynced;

    @Schema(name = "spaceDeleted", description = "本体空间索引清理的孤儿文档数")
    private long spaceDeleted;

    @Schema(name = "metaSynced", description = "本体元数据索引写入文档数")
    private long metaSynced;

    @Schema(name = "metaDeleted", description = "本体元数据索引清理的孤儿文档数")
    private long metaDeleted;

    @Schema(name = "propertySynced", description = "属性索引写入文档数")
    private long propertySynced;

    @Schema(name = "propertyDeleted", description = "属性索引清理的孤儿文档数")
    private long propertyDeleted;

    @Schema(name = "instanceSynced", description = "实例索引写入文档数")
    private long instanceSynced;

    @Schema(name = "instanceDeleted", description = "实例索引清理的孤儿文档数")
    private long instanceDeleted;

    @Schema(name = "costMillis", description = "同步耗时（毫秒）")
    private long costMillis;
}
