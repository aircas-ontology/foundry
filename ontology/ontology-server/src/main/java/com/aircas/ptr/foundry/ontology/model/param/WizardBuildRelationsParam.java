package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.dto.WizardObjectSpecDTO;
import com.aircas.ptr.foundry.ontology.model.dto.WizardPropertyDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 向导步骤 5：构建关系 - 请求参数
 * <p>
 * 除了新对象规格与属性列表外，后端还会自动加载同空间下已有本体作为候选目标。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "向导步骤5-构建关系请求参数")
public class WizardBuildRelationsParam {

    @Schema(name = "spaceId", description = "本体空间id", required = true, example = "1")
    @NotNull(message = "空间id不能为空")
    @SpaceIdVerify
    private Integer spaceId;

    @Schema(name = "userInput", description = "步骤1用户输入的自然语言描述", required = true)
    @NotBlank(message = "用户输入不能为空")
    @Size(max = 2000, message = "用户输入过长，最多 2000 字符")
    private String userInput;

    @Schema(name = "datasourceId", description = "步骤2选择的数据源id", required = true, example = "1")
    @NotNull(message = "数据源id不能为空")
    private Integer datasourceId;

    @Schema(name = "objectSpec", description = "步骤3产出的对象规格", required = true)
    @NotNull(message = "对象规格不能为空")
    @Valid
    private WizardObjectSpecDTO objectSpec;

    @Schema(name = "properties", description = "步骤4产出的属性列表（可作为 LLM 判断关系的额外上下文）")
    @Valid
    private List<WizardPropertyDTO> properties;
}
