package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.QueryOpEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 创建子空间请求参数：基于已有空间选择对象、实例、属性（含筛选条件）和关系，生成新的子空间。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建子空间请求")
public class OntologySubspaceCreateParam {

    @Schema(name = "parentSpaceId", description = "父空间 id（源空间）", example = "1", required = true)
    @NotNull(message = "parentSpaceId is empty")
    @SpaceIdVerify
    private Integer parentSpaceId;

    @Schema(name = "displayName", description = "子空间显示名称", example = "海军本体空间子空间", required = true)
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "apiName", description = "子空间 api 名称", example = "subspace_1791431719233", required = true)
    @NotBlank(message = "apiName is empty")
    private String apiName;

    @Schema(name = "description", description = "子空间描述")
    private String description;

    @Schema(name = "iconUrl", description = "子空间图标 url")
    private String iconUrl;

    @Schema(name = "selectedOntologies", description = "选中的对象配置", required = true)
    @NotEmpty(message = "selectedOntologies is empty")
    @Valid
    private List<OntologySelection> selectedOntologies;

    @Schema(name = "selectedLinks", description = "选中的关系配置")
    @Valid
    private List<LinkSelection> selectedLinks;

    /**
     * 选中的本体对象。
     */
    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "选中的本体对象")
    public static class OntologySelection {

        @Schema(name = "sourceOntologyUniqueIdentifier", description = "源空间中被选中的本体 uniqueIdentifier", required = true)
        @NotBlank(message = "sourceOntologyUniqueIdentifier is empty")
        @OntologyIdVerify
        private String sourceOntologyUniqueIdentifier;

        @Schema(name = "selectedInstancePrimaryKeys", description = "显式勾选的实例主键列表；为空表示按属性筛选条件导入全部匹配实例")
        private List<Object> selectedInstancePrimaryKeys;

        @Schema(name = "selectedProperties", description = "选中的属性及筛选条件", required = true)
        @NotEmpty(message = "selectedProperties is empty")
        @Valid
        private List<PropertySelection> selectedProperties;
    }

    /**
     * 选中的属性及筛选条件。
     */
    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "选中的属性及筛选条件")
    public static class PropertySelection {

        @Schema(name = "sourcePropertyUniqueIdentifier", description = "源空间中被选中的属性 uniqueIdentifier", required = true)
        @NotBlank(message = "sourcePropertyUniqueIdentifier is empty")
        private String sourcePropertyUniqueIdentifier;

        @Schema(name = "filter", description = "属性筛选条件；不配置筛选时传 null")
        @Valid
        private PropertyFilterConfig filter;
    }

    /**
     * 属性筛选条件。
     */
    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "属性筛选条件")
    public static class PropertyFilterConfig {

        @Schema(name = "op", description = "筛选操作符", example = "LIKE", required = true)
        @NotNull(message = "op is empty")
        private QueryOpEnum op;

        @Schema(name = "value", description = "筛选值：单值 op（EQ/LIKE/GT 等）传标量，如 \"驱逐舰\" 或 3000；多值 op（IN/BETWEEN 等）传数组，如 [\"福特\",\"通用\"] 或 [3000,10000]（BETWEEN 约定 [lower, upper]）。统一用本字段，不再有 values 字段")
        private Object value;

        @Schema(name = "dataType", description = "属性数据类型", example = "String", required = true)
        @NotNull(message = "dataType is empty")
        private OntologyDataTypeEnum dataType;
    }

    /**
     * 选中的关系。
     */
    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "选中的关系")
    public static class LinkSelection {

        @Schema(name = "sourceLinkUniqueIdentifier", description = "源空间中被选中的关系 uniqueIdentifier", required = true)
        @NotBlank(message = "sourceLinkUniqueIdentifier is empty")
        private String sourceLinkUniqueIdentifier;

        @Schema(name = "name", description = "新关系名称；为空则沿用源关系名称")
        private String name;

        @Schema(name = "apiName", description = "新关系 api 名称；为空则自动生成")
        private String apiName;

        @Schema(name = "type", description = "新关系类型；为空则沿用源关系类型")
        private com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum type;

        @Schema(name = "description", description = "新关系描述")
        private String description;
    }
}
