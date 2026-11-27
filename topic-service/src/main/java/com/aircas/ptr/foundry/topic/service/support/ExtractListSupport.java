package com.aircas.ptr.foundry.topic.service.support;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.vo.ExtractItemVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽取列表 JSON：[{"name":"实体","quantity":1,"proportion":0.25}]
 */
public final class ExtractListSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExtractListSupport() {
    }

    public static void check(String extractList) {
        read(extractList);
    }

    public static List<ExtractItemVO> read(String extractList) {
        JsonNode node;
        try {
            node = MAPPER.readTree(extractList);
        } catch (Exception ex) {
            node = null;
        }
        PreconditionUtils.checkArgument(node != null && node.isArray(), "抽取列表格式不正确", HttpStatus.BAD_REQUEST);
        List<ExtractItemVO> items = new ArrayList<>();
        for (JsonNode item : node) {
            boolean valid = item.isObject()
                    && item.hasNonNull("name") && item.get("name").isTextual() && !item.get("name").asText().isBlank()
                    && item.has("quantity") && item.get("quantity").isIntegralNumber() && item.get("quantity").asInt() >= 0
                    && item.has("proportion") && item.get("proportion").isNumber();
            PreconditionUtils.checkArgument(valid, "抽取列表格式不正确", HttpStatus.BAD_REQUEST);
            items.add(ExtractItemVO.builder()
                    .name(item.get("name").asText())
                    .quantity(item.get("quantity").asInt())
                    .proportion(item.get("proportion").decimalValue())
                    .build());
        }
        return items;
    }
}
