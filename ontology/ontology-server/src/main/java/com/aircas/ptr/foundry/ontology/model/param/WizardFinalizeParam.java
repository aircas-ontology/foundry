package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.dto.WizardObjectSpecDTO;
import com.aircas.ptr.foundry.ontology.model.dto.WizardPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.vo.WizardRelationVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 向导完成 - 落库请求参数
 * <p>
 * 前端在五步向导最后点击"完成"时调用，将步骤 3/4/5 的产出（可能经用户修改）一次性落库。
 * reasoning（构建依据）字段前端只读，落库时不持久化，仅作为流程上下文。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "向导完成-落库请求参数")
public class WizardFinalizeParam {

    @Schema(name = "spaceId", description = "本体空间id", required = true, example = "1")
    @NotNull(message = "空间id不能为空")
    @SpaceIdVerify
    private Integer spaceId;

    @Schema(name = "objectSpec", description = "步骤3产出的对象规格（前端可修改 name/apiName/description/categoryId，reasoning 只读）", required = true)
    @NotNull(message = "对象规格不能为空")
    @Valid
    private WizardObjectSpecDTO objectSpec;

    @Schema(name = "properties", description = "步骤4产出的属性列表（前端可修改 name/summary/field/type，reasoning 只读）")
    @Valid
    private List<WizardPropertyDTO> properties;

    @Schema(name = "relations", description = "步骤5产出的关系列表（前端可修改 name/type/description，reasoning 只读）")
    @Valid
    private List<WizardRelationVO> relations;
}
