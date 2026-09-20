package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "link create param")
public class OntologyLinkCreateParam {

    /**
     * link的名称
     */
    @Schema(name = "name", required = true, description = "本体关系名称")
    @NotBlank(message = "link name is empty")
    private String name;

    /**
     * 开始本体unique identifier
     */
    @Schema(name = "ontologyUniqueIdentifierFrom", required = true, description = "开始本体uniq id")
    @NotBlank(message = "ontologyUniqueIdentifierFrom name is empty")
    @OntologyIdVerify
    private String ontologyUniqueIdentifierFrom;

    /**
     * 结束本体unique identifier
     */
    @Schema(name = "ontologyUniqueIdentifierTo", required = true, description = "结束本体uniq id")
    @NotBlank(message = "ontologyUniqueIdentifierTo name is empty")
    @OntologyIdVerify
    private String ontologyUniqueIdentifierTo;


    @Schema(name = "type", required = true, description = "关系类型")
    @NotNull(message = "type is empty")
    private OntologyLinkTypeEnum type;

    /**
     * 关系分类id（ontology_link_category.id），选填；不传则不归属任何分类
     */
    @Schema(name = "categoryId", required = false, description = "关系分类id（ontology_link_category.id），选填", example = "1")
    private Integer categoryId;

    /**
     * 关系在代码中使用的api名称（创建时必填）
     */
    @Schema(name = "apiName",required = true, description = "关系api名称（创建时必填）",example = "api")
    @NotBlank(message = "apiName is empty")
    private String apiName;

    /**
     * 关系备注/描述
     */
    @Schema(name = "comment", required = false, description = "关系备注/描述",example = "comment")
    private String comment;

    /**
     * 关系所属空间id（ontology_space.id），由调用方直接传入，不再反查本体归属
     */
    @Schema(name = "spaceId", required = true, description = "关系所属空间id（ontology_space.id）", example = "1")
    @NotNull(message = "spaceId is empty")
    private Integer spaceId;



}
