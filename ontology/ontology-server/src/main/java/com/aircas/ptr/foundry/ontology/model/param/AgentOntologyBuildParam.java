package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Agent 本体构建落库入参。
 *
 * <p>对应「最终落库」阶段：Agent 在完成对象定义（功能二）、属性选择（功能三）、关系选择（功能四）后，
 * 把汇总结果一次性提交落库。编排方式模仿画布一键建空间
 * （{@code OntologySpaceServiceImpl#createSpaceWithCanvasContent}），但**不创建新空间**——
 * 空间已由用户选定（spaceId），仅在该空间下创建一个本体对象及其属性、关系。</p>
 *
 * <p>字段命名与画布参数保持一致语义：displayName 为中文展示名，apiName 为代码用英文标识，
 * 属性 apiName 直接取数据库列名（snake_case，符合 {@code ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$}）。</p>
 */
@Schema(description = "Agent 本体构建落库请求：在已有空间下创建一个本体对象及其属性、关系")
@Data
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class AgentOntologyBuildParam {

    @Schema(name = "spaceId", description = "本体空间 id（已存在，不新建）", required = true, example = "1")
    @NotNull(message = "spaceId is empty")
    private Integer spaceId;

    @Schema(name = "categoryId", description = "本体分类 id（ontology_category.id），无合适分类时传 null", example = "1")
    private Integer categoryId;

    @Schema(name = "displayName", description = "本体名称（中文展示名）", required = true, example = "用户")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "apiName", description = "本体 api 名称（代码用英文标识）", required = true, example = "user")
    @NotBlank(message = "apiName is empty")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "本体apiName格式不合法")
    private String apiName;

    @Schema(name = "description", description = "本体描述", example = "系统用户")
    private String description;

    @Schema(name = "sourceTable", description = "对象来源表名（溯源用，可为空）", example = "sys_user")
    private String sourceTable;

    @Schema(name = "properties", description = "属性列表（用户已勾选）")
    @Valid
    private List<AgentProperty> properties;

    @Schema(name = "relations", description = "关系列表（用户已勾选，目标为同空间已有本体）")
    @Valid
    private List<AgentRelation> relations;

    @Schema(description = "Agent 本体属性")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AgentProperty {

        @Schema(name = "displayName", description = "属性展示名称", required = true, example = "用户名")
        @NotBlank(message = "property displayName is empty")
        private String displayName;

        @Schema(name = "apiName", description = "属性 api 名称（取数据库列名）", required = true, example = "user_name")
        @NotBlank(message = "property apiName is empty")
        @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "属性apiName格式不合法")
        private String apiName;

        @Schema(name = "dataType", description = "数据库原生类型字符串（如 varchar/int8/timestamp），落库时归一化为 OntologyDataTypeEnum，空则默认 String", example = "varchar")
        private String dataType;

        @Schema(name = "description", description = "属性描述", example = "用户登录名")
        private String description;

        @Schema(name = "isPrimaryKey", description = "是否主键，不传默认 false")
        private Boolean isPrimaryKey;

        @Schema(name = "isTitleKey", description = "是否名称键，不传默认 false")
        private Boolean isTitleKey;

        @Schema(name = "sourceTable", description = "属性来源表名（溯源用，可为空）", example = "sys_user")
        private String sourceTable;

        @Schema(name = "viaField", description = "若属性来自关联表，标记通过主表哪个逻辑外键列推导（溯源用，可为空）", example = "dept_id")
        private String viaField;
    }

    @Schema(description = "Agent 本体关系")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AgentRelation {

        @Schema(name = "name", description = "关系名称", required = true, example = "用户归属部门")
        @NotBlank(message = "relation name is empty")
        private String name;

        @Schema(name = "targetUniqueIdentifier", description = "目标本体（同空间已有对象）的 uniqueIdentifier", required = true, example = "8039c5f9-5579-4ee4-ba94-b2f25f785dd6")
        @NotBlank(message = "targetUniqueIdentifier is empty")
        private String targetUniqueIdentifier;

        @Schema(name = "type", description = "关系类型（OntologyLinkTypeEnum 名称：COMPOSITION/POSSESSION/ATTRIBUTION 等），不传默认 OTHER", example = "ATTRIBUTION")
        private String type;

        @Schema(name = "categoryId", description = "关系分类 id（ontology_link_category.id），不传则挂到本空间默认关系分类")
        private Integer categoryId;
    }
}
