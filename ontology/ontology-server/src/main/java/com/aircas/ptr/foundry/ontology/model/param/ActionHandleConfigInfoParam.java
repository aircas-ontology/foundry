package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为执行配置参数")
public class ActionHandleConfigInfoParam {

    /** 调度按 actionApi 定位行为，不校验对象 id；多对象行为回显的是逗号拼接串，无法通过单对象校验 */
    @Schema(name = "ontologyIdentifier", description = "对象 id（行为挂载的对象；应用行为可不传，从 linkMapping 推导）", example = "abcdef")
    private String ontologyIdentifier;

    @Schema(name = "actionApi", description = "行为api", required = true, example = "shipLocation")
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @Schema(name = "name", description = "行为调度name", required = true, example = "name")
    @NotBlank(message = "name is empty")
    private String name;

    @Schema(name = "description", description = "行为调度描述", required = true, example = "description")
    private String description;

    @Schema(name = "type", description = "行为调度类型", required = true, example = "RULE")
    @NotNull(message = "type is empty")
    private ActionSchedulingTypeEnum type;

}
