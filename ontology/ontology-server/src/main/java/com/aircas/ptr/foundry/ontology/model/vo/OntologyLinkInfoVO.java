package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;


@Data
@Builder
@Accessors(chain = true)
@Schema(description = "本体关系数据信息")
public class OntologyLinkInfoVO {


    /**
     * link的unique identifier
     */
    @Schema(name = "uniqueIdentifier", description = "link 唯一标识")
    private String uniqueIdentifier;

    /**
     * link的名称
     */
    @Schema(name = "name", description = "link名称")
    private String name;

    /**
     * 开始本体unique identifier
     */
    @Schema(name = "ontologyUniqueIdentifierFrom", description = "开始本体unique identifier")
    private String ontologyUniqueIdentifierFrom;

    /**
     * 开始本体名称
     */
    @Schema(name = "ontologyNameFrom", description = "开始本体名称")
    private String ontologyNameFrom;

    /**
     * 开始本体icon
     */
    @Schema(name = "ontologyIconFrom", description = "开始本体icon")
    private String ontologyIconFrom;

    /**
     * 结束本体id
     */
    @Schema(name = "ontologyUniqueIdentifierTo", description = "结束本体id")
    private String ontologyUniqueIdentifierTo;

    /**
     * 结束本体名称
     */
    @Schema(name = "ontologyNameTo", description = "结束本体名称")
    private String ontologyNameTo;

    /**
     * 开始本体icon
     */
    @Schema(name = "ontologyIconTO", description = "开始本体icon")
    private String ontologyIconTO;

    /**
     * 关系分类id（ontology_link_category.id），为空表示未分类
     */
    @Schema(name = "categoryId", description = "关系分类id（ontology_link_category.id）")
    private Integer categoryId;

}


