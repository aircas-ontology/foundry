package com.aircas.ptr.foundry.ontology.model.param;

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
 * 表智能匹配请求参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "表智能匹配请求参数")
public class TableMatchParam {

    @Schema(name = "userInput", description = "用户自然语言描述", required = true,
            example = "构建一个卫星本体对象")
    @NotBlank(message = "userInput is empty")
    @Size(max = 500, message = "userInput too long, max 500 chars")
    private String userInput;

    @Schema(name = "datasourceId", description = "数据源连接id（datasource_connection.id）",
            required = true, example = "1")
    @NotNull(message = "datasourceId is null")
    private Integer datasourceId;
}
