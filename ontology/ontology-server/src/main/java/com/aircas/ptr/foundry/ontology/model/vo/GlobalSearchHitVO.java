package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "全局检索命中项")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalSearchHitVO {

    @Schema(name = "id", description = "文档 id：本体/空间/属性/关系分组为数据库主键，实例对象为 pk（{schema}.{table}.{ontology_uid}.{主键值}）")
    private String id;

    @Schema(name = "index", description = "来源索引：ontology_space / ontology_meta / ontology_property / ontology_instance / ontology_link_group")
    private String index;

    @Schema(name = "score", description = "相关性得分")
    private Double score;
}
