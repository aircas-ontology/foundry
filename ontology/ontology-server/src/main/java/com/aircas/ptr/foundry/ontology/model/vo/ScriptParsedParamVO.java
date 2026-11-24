package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 脚本参数解析结果的单个参数。业务字段与创建/更新接口的参数元素（{@code FunctionParamDTO}）一致，
 * 前端拿到后可直接放进函数保存请求的 {@code parameters} 数组；{@code paramType} 直接携带类型文本
 * （集合参数为 {@code List<元素>}），{@code elementType} 仅为渲染便利的附加字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "脚本参数解析结果-单个参数")
public class ScriptParsedParamVO {

    @Schema(description = "参数名称")
    private String paramName;

    @Schema(description = "参数类型文本：集合参数带元素类型（如 List<Double>），其余为枚举名（DOUBLE/OBJECT）")
    private String paramType;

    @Schema(description = "参数输入输出类别：INPUT/OUTPUT")
    private FunctionParamCategoryEnum category;

    @Schema(description = "参数顺序（同 category 内从 1 起）")
    private Integer paramOrder;

    @Schema(description = "全限定类名：Java/Groovy 按声明类型推导（如 java.lang.String）；Python/TS 无 Java 类概念，置空表示执行时按原始 JSON 透传")
    private String referenceType;

    @Schema(description = "集合参数的元素类型文本（List<Double> → Double、List<ShTrack> → ShTrack），标量/对象参数为 null；"
            + "仅供展示便利，保存接口忽略该字段，paramType 已直接携带 List<元素> 文本")
    private String elementType;

    @Schema(description = "参数结构 JSON Schema（静态解析不产出，为 null；由用户保存前自行补充）")
    private String paramSchema;

    @Schema(description = "参数描述（静态解析不产出，为 null；由用户保存前自行补充）")
    private String description;

    @Schema(description = "子字段列表（参数类型为自定义类/接口时展开，标量参数为 null）")
    private List<ScriptParsedParamVO> fields;
}
