package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Kafka Connect 连接器响应 DTO
 * 对应 GET /connectors/{name} 和 POST /connectors 的响应
 */
@Data
public class ConnectorDTO {
    private String name;
    private Map<String, Object> config;
    private List<Map<String, Object>> tasks;
    private String type;
    @lombok.Data
    public static class ConnectorState {
        private String name;
        private String type;
        private String connectorState;
        private List<Map<String, Object>> tasks;
    }
}
