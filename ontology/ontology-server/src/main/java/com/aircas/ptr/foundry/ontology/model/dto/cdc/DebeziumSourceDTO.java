package com.aircas.ptr.foundry.ontology.model.dto.cdc;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DebeziumSourceDTO {

    @JsonProperty("version")
    private String version;

    @JsonProperty("connector")
    private String connector;

    @JsonProperty("name")
    private String name;

    @JsonProperty("ts_ms")
    private Long tsMs;

    @JsonProperty("snapshot")
    private String snapshot;

    @JsonProperty("db")
    private String db;

    @JsonProperty("sequence")
    private String sequence;

    @JsonProperty("schema")
    private String schema;

    @JsonProperty("table")
    private String table;

    @JsonProperty("txId")
    private Long txId;

    @JsonProperty("lsn")
    private Long lsn;

    @JsonProperty("xmin")
    private Long xmin;
}