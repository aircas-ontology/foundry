package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@Schema(description = "实体属性值VO")
public class EntityPropertyVO {



    @Schema(name = "propertyApiName", description = "属性api名称", example = "名称")
    private String propertyApiName;


    @Schema(name = "propertyName", description = "属性名称", example = "名称")
    private String propertyDisplayName;


    @Schema(name = "propertyValue", description = "属性值", example = "123")
    private Object propertyValue;

}
