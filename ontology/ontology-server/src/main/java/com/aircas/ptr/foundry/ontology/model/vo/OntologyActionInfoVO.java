package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOperationEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOutputModeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSubjectTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "对象行为信息")
public class OntologyActionInfoVO {

    @Schema(name = "id", description = "行为id", example = "1")
    private Long id;

    @Schema(name = "icon", description = "对象icon", example = "http://127.0.0.1/a.jpeg")
    private String icon;

    @Schema(name = "ontologyUniqIdentifier", description = "对象id", example = "123456")
    private String ontologyUniqIdentifier;

    @Schema(name = "actionApi", description = "api名称", example = "satellite")
    private String actionApi;

    @Schema(name = "description", description = "行为描述", example = "这是一个行为")
    private String description;

    @Schema(name = "displayName", description = "行为显示名称", example = "调用函数")
    private String displayName;

    @Schema(name = "functionApi", description = "对象下函数api", example = "getInfo")
    private String functionApi;

    @Schema(name = "functionDescription", description = "函数描述", example = "这是一个函数")
    private String functionDescription;

    @Schema(name = "categoryId", description = "所属行为分类id", example = "1")
    private Integer categoryId;

    @Schema(name = "status", description = "状态：1 可用，0 失效（定义后目标表新增了必填属性，需重新编辑发布）", example = "1")
    private Integer status;

    @Schema(name = "actionKind", description = "行为类别：BASE 基础行为 / APPLICATION 应用行为", example = "BASE")
    private ActionKindEnum actionKind;

    @Schema(name = "subjectType", description = "基础行为类型：PROPERTY_VALUE 属性值 / LINK 实例关系 / BASIC_OPERATOR 基础算子")
    private ActionSubjectTypeEnum subjectType;

    @Schema(name = "operation", description = "基础操作：CREATE 新增 / UPDATE 修改 / DELETE 删除")
    private ActionOperationEnum operation;

    @Schema(name = "storageGroup", description = "属性值操作的目标存储分组", example = "extra")
    private String storageGroup;

    @Schema(name = "outputMode", description = "输出方式：RESULT_ONLY 仅返回结果 / WRITE_PROPERTY 写入对象属性", example = "RESULT_ONLY")
    private ActionOutputModeEnum outputMode;
}
