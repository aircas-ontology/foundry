package com.aircas.ptr.foundry.ontology.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyBuildDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyKnowledgeDTO;
import com.aircas.ptr.foundry.ontology.model.param.OntologyBuildQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyKnowledgeQueryParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyKnowledgeVO;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.HashMap;

@Tag(name = "本体rag flow")
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
@Validated
public class RagFlowController {

    @Value("${rag.knowledge-url}")
    private String ontologyKnowledgeUrl;

    @Value("${rag.build-url}")
    private String ontologyBuildUrl;


    @Operation(summary = "知识库问答")
    @PostMapping("/knowledge")
    public RestResult<OntologyKnowledgeVO> getOntologyKnowledge(@Valid @RequestBody OntologyKnowledgeQueryParam param) throws Exception {

        var knowledgeDTO = HttpUtil.postJson(ontologyKnowledgeUrl, new HashMap<>(), param, new TypeReference<OntologyKnowledgeDTO>() {
        });
        var res = OntologyKnowledgeVO.builder()
                .answer(knowledgeDTO.getAnswer())
                .evidence(knowledgeDTO.getMetadata().getEvidence())
                .build();
        return RestResult.ofData(res);
    }

    @Operation(summary = "格式化构建本体")
    @PostMapping("/build")
    public RestResult<OntologyBuildDTO> buildFormattedOntology(@Valid @RequestBody OntologyBuildQueryParam param) throws Exception {

        var buildDTO = HttpUtil.postJson(ontologyBuildUrl, new HashMap<>(), param, new TypeReference<OntologyBuildDTO>() {
        });


        return RestResult.ofData(buildDTO);
    }

}
