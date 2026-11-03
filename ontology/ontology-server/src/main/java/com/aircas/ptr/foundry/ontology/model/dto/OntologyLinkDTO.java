package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 空间导出的对象关系（ontology_link_group）。
 *
 * <p>导入时 uniqueIdentifier 会重新生成，端点对象与所属分类改用空间内唯一的 displayName、
 * 分类全路径表达，避免依赖导出时的主键与 id。</p>
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyLinkDTO {

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
     * 关系类型
     */
    private OntologyLinkTypeEnum type;

    /**
     * 开始对象的显示名称（同空间内唯一，导入时据此解析新对象id）
     */
    private String ontologyNameFrom;

    /**
     * 结束对象的显示名称
     */
    private String ontologyNameTo;

    /**
     * 关系分类全路径（ontology_link_category.path），为空表示未分类
     */
    private String categoryPath;

    /**
     * 该关系下的实例关系边（图库 relation 中该关系状态非 DELETE 的边及其状态、可见时间窗），
     * 仅 INSTANCE 模式导出时填充；未点亮的实例对在图库里没有边，因此这里就是需要搬运的全部边，
     * 导入时据此按实例主键映射精确落位
     */
    private List<EntityRelationExportDTO> entityRelations;
}
