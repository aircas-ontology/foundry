package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

import static com.aircas.ptr.foundry.ontology.constant.FunctionConstant.VERSION_PATTERN;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数请求")
public class FunctionCreateParam {

    @Schema(name = "functionApi", description = "函数api（创建后不可修改）")
    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @Schema(name = "version", description = "版本号，格式 x.y.z（如 1.0.0），同一 api 下不可重复且必须大于已有最大版本号", required = true)
    @NotBlank(message = "version is empty")
    @Pattern(regexp = VERSION_PATTERN, message = "版本号格式必须为 x.y.z（如 1.0.0）")
    private String version;

    @Schema(name = "displayName", description = "函数名称（必填，创建后不可修改）")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "description", description = "描述")
    private String description;

    @Schema(name = "type", description = "函数类型")
    @NotNull(message = "type is null")
    private FunctionTypeEnum type;

    @Schema(name = "language", description = "脚本语言（仅 CUSTOMIZE 脚本算子有效，不传默认 GROOVY；其他算子类型忽略此字段存 NULL；创建后不可修改）")
    private FunctionLanguageEnum language;

    @Schema(name = "model", description = "函数模型（可选，不传时根据 type 自动推断：BASIC_QUERY→BASIC，其他→OTHER）")
    private FunctionModelEnum model;

    @Schema(name = "code", description = "自定义函数：函数代码")
    private String code;

    @Schema(name = "referenceName", description = "外部函数：函数全限定名")
    private String referenceName;

    @Schema(name = "ontologySpaceId", description = "所属本体空间 id")
    private Integer ontologySpaceId;

    /**
     * 基础查询算子的查询模板配置，仅 type=BASIC_QUERY 时传递。
     * 后端将其序列化为 JSON 存入 code 字段，并自动提取变量存入 function_param。
     */
    @Schema(name = "queryConfig", description = "基础查询算子配置（仅 type=BASIC_QUERY 时必填）")
    private BasicQueryConfig queryConfig;

    /**
     * 前端 Web IDE 通过 AST 解析 handle 签名后提交的参数定义（入参/出参）。
     * 非 Groovy 语言依赖此字段建 function_param；Groovy 若不传可回退后端反射解析。
     */
    @Schema(name = "parameters", description = "参数定义列表（前端 IDE 解析 handle 签名后提交，CUSTOMIZE 推荐传入）")
    private List<FunctionParamDTO> parameters;

    /**
     * 函数编排的编排图定义，仅 type=COMPOSITE 时传递。
     * 后端将其序列化为 JSON 存入 code 字段，并根据输入/输出映射自动生成 function_param。
     */
    @Schema(name = "compositeConfig", description = "函数编排配置（仅 type=COMPOSITE 时必填）")
    private CompositeConfig compositeConfig;

    /**
     * 外部API算子的接口定义，仅 type=EXTERNAL 时传递。
     * 后端将其序列化为 JSON 存入 code 字段，并根据 inputs/outputs 自动生成 function_param，
     * 使外部API算子既能单独调用，也能作为函数编排画布节点接入工作流。
     */
    @Schema(name = "apiConfig", description = "外部API算子配置（仅 type=EXTERNAL 时必填）")
    private ExternalApiConfig apiConfig;

    /**
     * 是否基于旧版本创建函数。
     * true: 基于旧版本创建，允许相同 api 但 api + version 不能重复；
     * false/null: 全新创建，api 不能与任何已存在的函数重复。
     */
    @Schema(name = "basedOnOldVersion", description = "是否基于旧版本创建函数（true: 允许相同 api 但 api+version 不重复；false/null: 全新创建，api 不能重复）")
    private Boolean basedOnOldVersion;
}
