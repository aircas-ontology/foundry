package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOperationEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOutputModeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSubjectTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @author yangj
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "新增/编辑行为请求")
public class ActionCreateOrUpdateParam {

    /**
     * 行为（BASE/APPLICATION）必填，由 service 校验；行为树不指向对象类型，这里为空。
     * 因此不能再用基类的 @NotBlank，否则树请求进不了共用入口。
     */
    @OntologyIdVerify
    @Schema(name = "ontologyIdentifier", description = "对象id，即行为的操作范围；行为树不传", example = "abcdef")
    private String ontologyIdentifier;

    @Schema(name = "ontologySpaceId", description = "所属对象空间id；行为由对象id反查得到，行为树没有对象id，必须自己传", example = "1")
    private Integer ontologySpaceId;

    @Schema(name = "icon", description = "图片url", example = "http://192.168.9.11/aa.jpeg", required = true)
    private String icon;

    @Schema(description = "api名称", example = "satellite", required = true)
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @Schema(description = "对象下函数api；应用行为必填（CUSTOMIZE/EXTERNAL/CONTAINER），基础算子行为必填（BASIC_QUERY），属性值、实例关系行为不可传", example = "satellite")
    private String functionApi;

    @Schema(description = "行为描述", example = "这是一个行为")
    private String description;

    @Schema(description = "行为显示名称", example = "调用函数")
    @NotBlank(message = "displayName is empty")
    private String displayName;


    @Schema(name = "categoryId", description = "所属行为分类id，对应行为分类体系树的节点id；", example = "1")
    @NotNull(message = "categoryId is null")
    private Integer categoryId;

    @Schema(description = "行为类别：BASE 基础行为 / APPLICATION 应用行为 / TREE 行为树", example = "BASE", required = true)
    @NotNull(message = "actionKind is null")
    private ActionKindEnum actionKind;

    @Schema(description = "基础行为类型：PROPERTY_VALUE 属性值 / LINK 实例关系 / BASIC_OPERATOR 基础算子；actionKind=BASE 时必填")
    private ActionSubjectTypeEnum subjectType;

    @Schema(description = "基础操作：CREATE 新增 / UPDATE 修改 / DELETE 删除；属性值、实例关系两类必填，基础算子不可传")
    private ActionOperationEnum operation;

    @Schema(description = "目标存储分组（分组名即物理表名，main 主表不允许）；仅行为类型=属性值时必填")
    private String storageGroup;

    @Schema(description = "输出方式：RESULT_ONLY 仅返回结果 / WRITE_PROPERTY 写入对象属性；应用行为必填；属性值、实例关系行为不可传；基础算子固定为仅返回结果，可不传", example = "RESULT_ONLY")
    private ActionOutputModeEnum outputMode;

    @Schema(description = "行为关系映射")
    private ActionLinkMappingParam linkMapping;


    @Schema(description = "行为参数列表")
    private List<ActionParamMappingCreateParam> mappingIns;

    @Schema(description = "行为树定义（黑板 + 变量 + 节点树），actionKind=TREE 时必填")
    private BehaviorTreeDefinition treeDefinition;
}
