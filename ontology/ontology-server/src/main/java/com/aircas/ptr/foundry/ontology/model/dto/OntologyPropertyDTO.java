package com.aircas.ptr.foundry.ontology.model.dto;


import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyPropertyDTO {

    @Schema(name = "dataType", description = "数据类型", required = true, example = "Bool")
    private OntologyDataTypeEnum dataType;

    @Schema(name = "description", description = "列描述", required = true, example = "名称")
    private String description;

    @Schema(name = "displayName", description = "属性展示名称", example = "飞机", required = true)
    private String displayName;

    @Schema(name = "apiName", description = "在代码里用的属性名称，格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", required = true, example = "name")
    private String apiName;

    @Schema(name = "isPrimaryKey", description = "是否为主键", required = true, example = "true")
    private Boolean isPrimaryKey;

    @Schema(name = "isTitleKey", description = "是否为名称键", required = true, example = "true")
    private Boolean isTitleKey;

    @Schema(name = "type", description = "属性的自定义标签", example = "载荷基本信息")
    private String tag;

    @Schema(name = "defaultValue", description = "属性的默认值", example = "30")
    private String defaultValue;

    @Schema(name = "storageGroup", description = "属性存储分组", example = "123")
    private String storageGroup;

    @Schema(name = "categoryPath", description = "属性分类完整路径", example = "平台/载荷/光学载荷")
    private String categoryPath;

    @Schema(name = "metadata", description = "属性元数据")
    private JsonNode metadata;
}
