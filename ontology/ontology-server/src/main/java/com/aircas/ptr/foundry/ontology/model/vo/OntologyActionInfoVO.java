package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOperationEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOutputModeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSubjectTypeEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

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

    @Schema(name = "status", description = "状态：2 草稿（未发布，智能体和调度不可用）/ 1 已发布", example = "1")
    private Integer status;

    @Schema(name = "actionKind", description = "行为类别：BASE 基础行为 / APPLICATION 应用行为", example = "BASE")
    private ActionKindEnum actionKind;

    @Schema(name = "subjectType", description = "基础行为类型：PROPERTY_VALUE 属性值 / LINK 实例关系 / BASIC_OPERATOR 基础算子")
    private ActionSubjectTypeEnum subjectType;

    @Schema(name = "operation", description = "基础操作：CREATE 新增 / UPDATE 修改 / DELETE 删除")
    private ActionOperationEnum operation;

    @Schema(name = "storageGroup", description = "属性值操作的目标存储分组", example = "extra")
    private String storageGroup;

    @Schema(name = "outputMode", description = "输出方式（仅基础算子行为有值 RESULT_ONLY）；应用行为已下沉到 mappingIns 每行独立选择", example = "RESULT_ONLY")
    private ActionOutputModeEnum outputMode;

    @Schema(name = "createTime", description = "创建时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    @Schema(name = "updateTime", description = "修改时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;
}
