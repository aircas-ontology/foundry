package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.TreeOutputTargetModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 行为节点的出参：写回的给出数据位置，仅返回的只报名。
 * <p>调用方据此在 nodeExecutes[节点标识] 里按对应字段给实例和分组行主键；
 * 不给实例/行时回退执行请求里的全树默认值，出参没有默认目标时按原口径写。
 * RESULT_ONLY 的出参随执行结果返回，不需要调用方给数据位置，只列出来让人看到节点有哪些出参。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为节点出参要求")
public class TreeOutputTargetVO {

    @Schema(name = "mode", description = "传参方式：OUTPUT_PARAM 用 nodeExecutes[节点].outputParams[key] 传；"
            + "DATA_PRIMARY_KEY 用 nodeExecutes[节点].dataPrimaryKey 传（属性值-修改/删除这类不绑算子的行为）；"
            + "RESULT_ONLY 仅随执行结果返回，不用给数据位置")
    private TreeOutputTargetModeEnum mode;

    @Schema(name = "key", description = "outputParams 的键：写回行的属性唯一标识；DATA_PRIMARY_KEY、RESULT_ONLY 方式为空",
            example = "8f1c...")
    private String key;

    @Schema(name = "parameterName", description = "该行对应的算子出参名；页面展示用，执行请求按 key 定位", example = "result")
    private String parameterName;

    @Schema(name = "displayName", description = "写回的属性显示名；不绑算子的行为用行为显示名", example = "单发成本")
    private String displayName;

    @Schema(name = "objectIdentifier", description = "写回目标对象的标识：调用方按这个对象选实例", example = "123cfa...")
    private String objectIdentifier;

    @Schema(name = "storageGroup", description = "写入的存储分组；main 表示主表，主表行就是实例本身，不用给行主键",
            example = "duty_log")
    private String storageGroup;

    @Schema(name = "rowRequired", description = "是否要指定该分组的子表行主键：非 main 分组的写回行要给行主键，"
            + "通配出参（取值路径含 [*]）逐元素新增行，不给", example = "true")
    private boolean rowRequired;
}
