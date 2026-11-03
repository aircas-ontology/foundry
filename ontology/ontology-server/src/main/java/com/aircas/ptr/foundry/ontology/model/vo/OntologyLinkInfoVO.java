package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;


@Data
@Builder
@Accessors(chain = true)
@Schema(description = "对象关系数据信息")
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
     * 开始对象unique identifier
     */
    @Schema(name = "ontologyUniqueIdentifierFrom", description = "开始对象unique identifier")
    private String ontologyUniqueIdentifierFrom;

    /**
     * 开始对象名称
     */
    @Schema(name = "ontologyNameFrom", description = "开始对象名称")
    private String ontologyNameFrom;

    /**
     * 开始对象icon
     */
    @Schema(name = "ontologyIconFrom", description = "开始对象icon")
    private String ontologyIconFrom;

    /**
     * 结束对象id
     */
    @Schema(name = "ontologyUniqueIdentifierTo", description = "结束对象id")
    private String ontologyUniqueIdentifierTo;

    /**
     * 结束对象名称
     */
    @Schema(name = "ontologyNameTo", description = "结束对象名称")
    private String ontologyNameTo;

    /**
     * 开始对象icon
     */
    @Schema(name = "ontologyIconTO", description = "开始对象icon")
    private String ontologyIconTO;

    /**
     * 关系分类id（ontology_link_category.id），为空表示未分类
     */
    @Schema(name = "categoryId", description = "关系分类id（ontology_link_category.id）")
    private Integer categoryId;

    /**
     * 关系在代码中使用的api名称
     */
    @Schema(name = "apiName", description = "关系api名称")
    private String apiName;

    /**
     * 关系备注/描述
     */
    @Schema(name = "description", description = "关系备注/描述")
    private String description;

}


