package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 基础函数多对象可选的公共字段：只保留每个选中对象都有、类型一致且都已关联数据源的属性。
 * <p>查询字段、聚合目标、过滤条件都只能从这批里选，否则跨对象 UNION ALL 合不起来。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "基础函数多对象的公共字段")
public class BasicFunctionCommonPropertyVO {

    @Schema(name = "ontologyIdentifiers", description = "本次参与取交集的对象id，与请求顺序一致",
            example = "[\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\",\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\"]")
    private List<String> ontologyIdentifiers;

    @Schema(name = "ontologyNames", description = "参与对象名称，与 ontologyIdentifiers 同序、逐项对应；对象名称为空时用对象id占位",
            example = "[\"卫星\",\"导弹\"]")
    private List<String> ontologyNames;

    @Schema(name = "properties", description = "公共字段，按第一个对象的属性顺序排列")
    private List<CommonProperty> properties;

    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "一个公共字段")
    public static class CommonProperty {

        @Schema(name = "apiName", description = "属性 apiName，配置里就填它", example = "speed")
        private String apiName;

        @Schema(name = "propertyType", description = "属性数据类型；各对象的类型完全一致才会出现在这里", example = "Double")
        private OntologyDataTypeEnum propertyType;

        @Schema(name = "isPrimaryKey", description = "是否主键；按第一个对象取", example = "false")
        private Boolean isPrimaryKey;

        @Schema(name = "isTitleKey", description = "是否名称键；按第一个对象取", example = "false")
        private Boolean isTitleKey;

        @Schema(name = "displayNames", description = "各对象上这个属性的展示名，与 ontologyIdentifiers 同序、逐项对应；"
                + "同名属性在不同对象上叫法可能不同", example = "[\"速度\",\"速率\"]")
        private List<String> displayNames;
    }
}