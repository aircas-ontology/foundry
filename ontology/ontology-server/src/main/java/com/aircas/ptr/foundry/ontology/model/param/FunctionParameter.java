package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "参数")
public class FunctionParameter {

    @Schema(name = "paramName", description = "参数名称")
    @NotBlank(message = "paramName is empty")
    private String paramName;

    @Schema(name = "paramType", description = "参数类型：枚举名（LIST/OBJECT/DOUBLE 等），也兼容展示文本（如 List<Double>）与前端别名（ARRAY/NUMBER）；LIST 类型时后端据此把 fields 组装为数组而非对象")
    private String paramType;

    @Schema(name = "paramValue", description = "参数值（标量参数直接取值；对象参数若逐字段填写可不传，由 fields 组装）。写 paramValue:null 表示显式把 null 传给脚本；省略该字段表示未赋值（脚本收到缺键）")
    private Object paramValue;

    /** JSON 中是否出现过 paramValue 字段：值为 null 时即「显式传 null」，与省略该字段（未赋值）区分。 */
    @JsonIgnore
    @Schema(hidden = true)
    private boolean paramValueSpecified;

    /**
     * Jackson 只在请求 JSON 里存在 paramValue 字段时调用本 setter（值为 null 也会调用），
     * 借此区分「显式传 null」与「没传该参数」。
     */
    public void setParamValue(Object paramValue) {
        this.paramValue = paramValue;
        this.paramValueSpecified = true;
    }

    @Schema(name = "fields", description = "子字段列表（对象/复合参数按层级逐字段填值时使用，后端据此递归组装成嵌套对象）")
    private List<FunctionParameter> fields;

    /** 执行链路用的类型枚举，按 paramType 文本还原（展示文本 List&lt;Double&gt; 归一为 LIST）。 */
    public FunctionParamTypeEnum paramTypeEnum() {
        return FunctionParamTypeEnum.fromTypeText(paramType);
    }
}
