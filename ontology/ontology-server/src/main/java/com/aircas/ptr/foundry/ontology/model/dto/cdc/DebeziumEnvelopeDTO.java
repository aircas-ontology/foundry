package com.aircas.ptr.foundry.ontology.model.dto.cdc;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DebeziumEnvelopeDTO<T> {

    /** 变更前数据，insert 时为 null */
    @JsonProperty("before")
    private T before;

    /** 变更后数据，delete 时为 null */
    @JsonProperty("after")
    private T after;

    /** 元数据 */
    @JsonProperty("source")
    private DebeziumSourceDTO source;

    /** 操作类型：c=create, u=update, d=delete, r=read */
    @JsonProperty("op")
    private String op;

    /** 事件时间戳（毫秒） */
    @JsonProperty("ts_ms")
    private Long tsMs;

    /** 事务信息，通常为 null */
    @JsonProperty("transaction")
    private Object transaction;
}