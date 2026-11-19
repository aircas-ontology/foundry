package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ActionOperationEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 基础函数行为一次执行的结果回写情况。
 * <p>没配回写时整个对象为空；配了回写就一定有值，落了哪张表、哪个实例、几行、写了哪些属性都写清楚，
 * 页面据此判断这次到底有没有落库，不用再去翻数据。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "基础函数行为结果回写情况")
public class ActionBasicFunctionWriteBackVO {

    @Schema(name = "targetOntologyIdentifier", description = "回写目标对象id", example = "3f2a9c1e-7b4d-48ce-b029-8ab05dae780c")
    private String targetOntologyIdentifier;

    @Schema(name = "targetOntologyDisplayName", description = "回写目标对象名称", example = "卫星")
    private String targetOntologyDisplayName;

    @Schema(name = "targetStorageGroup", description = "回写落到的存储分组；聚合方式固定 main", example = "track")
    private String targetStorageGroup;

    @Schema(name = "operation", description = "落库方式：CREATE 往子表分组追加行 / UPDATE 改主表属性", example = "CREATE")
    private ActionOperationEnum operation;

    @Schema(name = "entityPrimaryKey", description = "本次回写落到哪个实例的数据上（子表行的归属实例、被改的主表行）", example = "12")
    private Object entityPrimaryKey;

    @Schema(name = "rowCount", description = "写入的数据行数；查询方式是追加的行数，聚合方式恒为 1", example = "20")
    private Integer rowCount;

    @Schema(name = "writtenProperties", description = "本次实际写入的目标属性名称", example = "[\"速度\",\"观测时间\"]")
    private List<String> writtenProperties;

    @Schema(name = "notWrittenFields", description = "查询字段里没有配到目标属性、本次没有落库的字段名称；聚合方式恒为空",
            example = "[\"经度\"]")
    private List<String> notWrittenFields;
}
