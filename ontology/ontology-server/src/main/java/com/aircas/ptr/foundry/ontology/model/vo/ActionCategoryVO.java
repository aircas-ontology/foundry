package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 行为分类体系树节点 VO。
 * <p>
 * 与 {@link OntologyCategoryVO} 的区别在于叶子挂载的对象：本体分类下挂本体（OntologyMetaInfoVO），
 * 行为分类下挂行为（OntologyActionInfoVO）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "行为分类VO")
public class ActionCategoryVO {

    @Schema(name = "categoryId", description = "行为分类id", example = "1")
    private Integer categoryId;

    @Schema(name = "name", description = "行为分类名称", example = "轨道机动")
    private String name;

    @Schema(name = "actionInfos", description = "该分类下的行为信息")
    private List<OntologyActionInfoVO> actionInfos;

    @Schema(name = "children", description = "子分类")
    private List<ActionCategoryVO> children;
}
