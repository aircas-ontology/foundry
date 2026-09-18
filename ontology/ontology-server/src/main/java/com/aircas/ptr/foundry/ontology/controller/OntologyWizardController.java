package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.dto.WizardObjectSpecDTO;
import com.aircas.ptr.foundry.ontology.model.dto.WizardPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildObjectParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildPropertiesParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildRelationsParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardFinalizeParam;
import com.aircas.ptr.foundry.ontology.model.vo.WizardFinalizeResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.WizardRelationVO;
import com.aircas.ptr.foundry.ontology.service.OntologyWizardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本体构建向导接口
 * <p>
 * 五步流程中的三个后端 LLM 步骤：
 * <ul>
 *   <li>步骤 3：POST /ontology-wizard/build-object</li>
 *   <li>步骤 4：POST /ontology-wizard/build-properties</li>
 *   <li>步骤 5：POST /ontology-wizard/build-relations</li>
 *   <li>完成：POST /ontology-wizard/persist（落库）</li>
 * </ul>
 * 每一步的响应中包含 reasoning（构建依据），前端只读展示但需原样回传给下一步。
 */
@Tag(name = "本体构建向导")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/ontology-wizard")
public class OntologyWizardController {

    private final OntologyWizardService ontologyWizardService;

    @PostMapping("/build-object")
    @Operation(summary = "步骤3-构建本体对象（返回名称/标识/描述/分类/构建依据）")
    public RestResult<WizardObjectSpecDTO> buildObject(
            @RequestBody @Valid WizardBuildObjectParam param) {
        return RestResult.ofData(ontologyWizardService.buildObject(param));
    }

    @PostMapping("/build-properties")
    @Operation(summary = "步骤4-构建本体属性（返回名称/摘要/字段/类型/构建依据）")
    public RestResult<List<WizardPropertyDTO>> buildProperties(
            @RequestBody @Valid WizardBuildPropertiesParam param) {
        return RestResult.ofData(ontologyWizardService.buildProperties(param));
    }

    @PostMapping("/build-relations")
    @Operation(summary = "步骤5-构建本体关系（返回关系名/源/目标/类型/说明/构建依据）")
    public RestResult<List<WizardRelationVO>> buildRelations(
            @RequestBody @Valid WizardBuildRelationsParam param) {
        return RestResult.ofData(ontologyWizardService.buildRelations(param));
    }

    @PostMapping("/persist")
    @Operation(summary = "完成-落库（将步骤3/4/5产出一次性持久化）")
    public RestResult<WizardFinalizeResultVO> persist(
            @RequestBody @Valid WizardFinalizeParam param) {
        return RestResult.ofData(ontologyWizardService.persist(param));
    }
}
