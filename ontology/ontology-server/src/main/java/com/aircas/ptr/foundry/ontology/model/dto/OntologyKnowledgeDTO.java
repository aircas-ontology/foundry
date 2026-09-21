package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OntologyKnowledgeDTO {

    private String answer;

    private KnowledgeMeta metadata;

    private String question;

    private List<KnowledgeSource> sources;


    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KnowledgeMeta {

        private String elasticsearch_index;

        private List<KnowledgeEvidence> evidence;

        private Integer evidence_count;
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KnowledgeEvidence {

        private String document;

        private String text;

        private Double score;
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KnowledgeSource {


        private String document;

        private Double score;

        private String snippet;
    }

}
