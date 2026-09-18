package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 向导步骤 3：构建本体对象 - 请求参数
 * <p>
 * 前置：步骤 1 用户填了描述、步骤 2 选了数据源，spaceId 从上下文获取。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "向导步骤3-构建对象请求参数")
public class WizardBuildObjectParam {

    @Schema(name = "spaceId", description = "本体空间id", required = true, example = "1")
    @NotNull(message = "空间id不能为空")
    @SpaceIdVerify
    private Integer spaceId;

    @Schema(name = "userInput", description = "步骤1用户输入的自然语言描述", required = true,
            example = "构建阿利·伯克级驱逐舰本体，覆盖船体结构、武器、传感器、动力与指控子系统")
    @NotBlank(message = "用户输入不能为空")
    @Size(max = 2000, message = "用户输入过长，最多 2000 字符")
    private String userInput;

    @Schema(name = "datasourceId", description = "步骤2选择的数据源id（datasource_connection.id）",
            required = true, example = "1")
    @NotNull(message = "数据源id不能为空")
    private Integer datasourceId;
}
