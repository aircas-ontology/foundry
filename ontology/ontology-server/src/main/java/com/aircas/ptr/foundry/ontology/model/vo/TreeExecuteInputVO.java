package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树执行时需要调用方关注的黑板键：由绑定行为的外部输入参数反推。
 * <p>键名与行为参数名一致，执行入参必须按这个键名传；有默认值或由行为回填的键不是必填。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行入参")
public class TreeExecuteInputVO {

    @Schema(name = "key", description = "黑板键名，也是执行入参 inputs 的键", example = "ammoCount")
    private String key;

    @Schema(name = "valueType", description = "黑板声明的值类型，未声明时取行为参数类型", example = "INTEGER")
    private FunctionParamTypeEnum valueType;

    @Schema(name = "defaultValue", description = "变量默认值；非空表示调用方不传也能执行", example = "10")
    private Object defaultValue;

    @Schema(name = "required", description = "是否必填：没有默认值且不是由行为回填的属性键", example = "true")
    private boolean required;

    @Schema(name = "bindProperty", description = "是否绑定对象属性；绑定的键由行为写入后回填，不接受执行入参", example = "false")
    private boolean bindProperty;

    @Schema(name = "description", description = "参数说明，取算子参数或属性的说明")
    private String description;

    @Schema(name = "nodeIds", description = "消费该参数的行为节点标识")
    private List<String> nodeIds = new ArrayList<>();

    @Schema(name = "actionApis", description = "声明该外部参数的行为 api")
    private List<String> actionApis = new ArrayList<>();

    public List<String> getNodeIds() {
        return nodeIds == null ? new ArrayList<>() : nodeIds;
    }

    public List<String> getActionApis() {
        return actionApis == null ? new ArrayList<>() : actionApis;
    }
}
