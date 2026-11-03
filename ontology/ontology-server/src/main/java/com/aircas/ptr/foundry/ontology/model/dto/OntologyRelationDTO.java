package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 单本体导出的对象关系（ontology_link_group）。
 *
 * <p>导入时 uniqueIdentifier 会重新生成，端点对象以空间内唯一的 displayName 表达，
 * 导入据此解析目标空间中已存在的对象；另一端对象不存在时该条关系跳过。</p>
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyRelationDTO {

    /**
     * 关系名称
     */
    private String name;

    /**
     * 关系在代码中使用的api名称
     */
    private String apiName;

    /**
     * 关系备注/描述
     */
    private String description;

    /**
     * 开始对象的显示名称
     */
    private String ontologyNameFrom;

    /**
     * 结束对象的显示名称
     */
    private String ontologyNameTo;

    /**
     * 关系类型
     */
    private OntologyLinkTypeEnum type;
}
