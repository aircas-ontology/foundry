package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "本体分组VO")
public class OntologyGroupInfoVO {

     @Schema(description = "分组名称", example = "远海远域")
     private String groupName;

     @Schema(description = "分组描述", example = "远海远域")
     private String description;

     @Schema(description = "分组id", example = "fdsafdsfaf")
     private String groupId;

     @Schema(description = "分组icon url", example = "icon")
     private String icon;

     @Schema(name = "spaceId", description = "本体空间id")
     private Integer spaceId;

}
