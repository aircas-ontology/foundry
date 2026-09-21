package com.aircas.ptr.foundry.ontology.model.vo;



import com.aircas.ptr.foundry.ontology.model.dto.OntologyKnowledgeDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "本体知识库vo")
public class OntologyKnowledgeVO {

    private String answer;


    private List<OntologyKnowledgeDTO.KnowledgeEvidence> evidence;
}
