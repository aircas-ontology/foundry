package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脚本参数解析结果的单个参数。字段与创建/更新接口的参数元素（{@code FunctionParamDTO}）完全一致，
 * 前端拿到后可直接放进函数保存请求的 {@code parameters} 数组，无需转换。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "脚本参数解析结果-单个参数")
public class ScriptParsedParamVO {

    @Schema(description = "参数名称")
    private String paramName;

    @Schema(description = "参数类型（归一化后的后端枚举名）")
    private FunctionParamTypeEnum paramType;

    @Schema(description = "参数输入输出类别：INPUT/OUTPUT")
    private FunctionParamCategoryEnum category;

    @Schema(description = "参数顺序（同 category 内从 1 起）")
    private Integer paramOrder;

    @Schema(description = "全限定类名：Java/Groovy 按声明类型推导（如 java.lang.String）；Python/TS 无 Java 类概念，置空表示执行时按原始 JSON 透传")
    private String referenceType;

    @Schema(description = "参数结构 JSON Schema（静态解析不产出，为 null；由用户保存前自行补充）")
    private String paramSchema;

    @Schema(description = "参数描述（静态解析不产出，为 null；由用户保存前自行补充）")
    private String description;
}
