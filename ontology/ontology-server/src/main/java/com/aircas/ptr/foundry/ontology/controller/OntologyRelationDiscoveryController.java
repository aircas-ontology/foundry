package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.OntologyRelationDiscoveryParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyRelationDiscoveryResultVO;
import com.aircas.ptr.foundry.ontology.service.OntologyRelationDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本体关系发现接口
 * <p>
 * 构建新本体时，判断它与同一空间下已有本体是否存在潜在关系。
 */
@Tag(name = "本体关系发现")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/ontology-relation")
public class OntologyRelationDiscoveryController {

    private final OntologyRelationDiscoveryService ontologyRelationDiscoveryService;

    @PostMapping("/discover")
    @Operation(summary = "发现新本体与同空间下已有本体的潜在关系")
    public RestResult<OntologyRelationDiscoveryResultVO> discover(
            @RequestBody @Valid OntologyRelationDiscoveryParam param) {
        return RestResult.ofData(ontologyRelationDiscoveryService.discover(param));
    }
}
