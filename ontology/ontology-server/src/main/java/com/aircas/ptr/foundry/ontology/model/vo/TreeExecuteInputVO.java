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
 * 行为树执行时需要调用方关注的外部入参：由绑定行为的外部输入参数反推。
 * <p>参数名与行为参数名一致：取值先看该节点的 nodeExecutes.params，没有再回退全树 inputs；
 * 只有配了变量默认值的参数可以不传。绑了对象属性的同名黑板键由条件求值时从库里读取，不能同时当行为入参传，执行前会按冲突报错。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行入参")
public class TreeExecuteInputVO {

    @Schema(name = "key", description = "参数名：行为的同名外部入参取值用它，也是全树默认值 inputs 的键", example = "ammoCount")
    private String key;

    @Schema(name = "valueType", description = "黑板声明的值类型，未声明时取行为参数类型", example = "INTEGER")
    private FunctionParamTypeEnum valueType;

    @Schema(name = "defaultValue", description = "变量默认值；非空表示调用方不传也能执行", example = "10")
    private Object defaultValue;

    @Schema(name = "required", description = "是否必填：没有配变量默认值就必须由执行入参给", example = "true")
    private boolean required;

    @Schema(name = "bindProperty", description = "该键是否绑了对象属性：绑属性的键由条件求值时从库里读当前值，"
            + "不能作为行为的外部入参，树里同时命中这两种来源时执行前按冲突报错", example = "false")
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
