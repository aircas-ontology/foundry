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

    @Schema(name = "name", description = "名称：空间/对象/属性取 display_name，实例取行名称，关系分组取 name")
    private String name;

    @Schema(name = "type", description = "命中类型：空间 / 对象 / 属性 / 实例 / 关系分组")
    private String type;

    @Schema(name = "uniqueIdentifier", description = "唯一标识：对象/属性/关系分组取 unique_identifier，实例取 ontology_uid，空间取 api_name（空间索引未存 unique_identifier 字段）")
    private String uniqueIdentifier;

    @Schema(name = "desc", description = "描述：空间/对象/属性取 description，实例取 search_text，关系分组为空")
    private String desc;

    @Schema(name = "spaceId", description = "所属本体空间 id")
    private Long spaceId;

    @Schema(name = "objectId", description = "所属对象（本体 meta）id：对象命中为自身 id，属性/实例命中为所属对象 id，其余为 null")
    private Long objectId;

    @Schema(name = "instanceId", description = "实例 id（pk，{schema}.{table}.{ontology_uid}.{主键值}），仅实例命中时有值")
    private String instanceId;

    @Schema(name = "propertyId", description = "属性 id，仅属性命中时有值")
    private Long propertyId;


}
