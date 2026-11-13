package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.TreeOutputTargetModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树里一个节点要写回的数据位置：写到哪个对象的哪条属性、哪个存储分组。
 * <p>调用方据此在 nodeExecutes[节点标识] 里按对应字段给实例和分组行主键；
 * 不给实例/行时回退执行请求里的全树默认值，出参没有默认目标时按原口径写。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树节点写回数据位置要求")
public class TreeOutputTargetVO {

    @Schema(name = "mode", description = "传参方式：OUTPUT_PARAM 用 nodeExecutes[节点].outputParams[key] 传；"
            + "DATA_PRIMARY_KEY 用 nodeExecutes[节点].dataPrimaryKey 传（属性值-修改/删除这类不绑算子的行为）")
    private TreeOutputTargetModeEnum mode;

    @Schema(name = "key", description = "outputParams 的键：写回行的属性唯一标识；DATA_PRIMARY_KEY 方式为空", example = "8f1c...")
    private String key;

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

    @Schema(name = "actionApi", description = "该写回行所属的行为 api", example = "act.satellite.track")
    private String actionApi;

    @Schema(name = "actionDisplayName", description = "该写回行所属的行为显示名称", example = "卫星跟踪")
    private String actionDisplayName;

    @Schema(name = "nodeIds", description = "绑定该行为、要按这条写回行传数据的节点标识")
    private List<String> nodeIds = new ArrayList<>();

    public List<String> getNodeIds() {
        return nodeIds == null ? new ArrayList<>() : nodeIds;
    }

}
