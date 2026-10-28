package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.model.enums.ScanSkipReason;
import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * function.scan_result 固定 JSON 契约的构造入口。
 *
 * <p>阶段一只需要生成完整的 SKIPPED 结果；完成真实扫描后的 PASSED/REJECTED
 * 结果由后续安全扫描策略层构造。这里显式写入 JSON null，避免全局 NON_NULL
 * 序列化配置造成持久化契约字段缺失。</p>
 */
public final class FunctionScanResultFactory {

    public static final int SCHEMA_VERSION = 1;

    private FunctionScanResultFactory() {
    }

    public static JsonNode skipped(ScanSkipReason reason) {
        ObjectNode summary = JsonNodeFactory.instance.objectNode()
                .put("total", 0)
                .put("high", 0)
                .put("medium", 0)
                .put("low", 0);

        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("schemaVersion", SCHEMA_VERSION);
        result.put("status", ScanStatus.SKIPPED.name());
        result.put("skipReason", reason.name());
        result.putNull("codeHash");
        result.putNull("scannedAt");
        result.putNull("scanId");
        result.putNull("durationMs");
        result.putNull("findingsTruncated");
        result.set("summary", summary);
        result.set("findings", JsonNodeFactory.instance.arrayNode());
        result.putNull("error");
        return result;
    }
}
