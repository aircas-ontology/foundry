package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CDCEventDTO {
    private Source source;
    private String op;
    private Long ts_ms;
    private String ddl;
    private Object before;
    private Object after;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        private String db;
        private String schema;
        private String table;
        private Long lsn;
        private Long ts_ms;
    }
}
