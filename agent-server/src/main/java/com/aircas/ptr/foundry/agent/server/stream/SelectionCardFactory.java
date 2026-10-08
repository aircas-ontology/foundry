package com.aircas.ptr.foundry.agent.server.stream;

import com.aircas.ptr.foundry.agent.server.model.vo.SelectionOptionVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 选择卡片选项工厂：把各类数据源（MCP 工具返回 / 已持久化的 proposed 状态块）统一转换为
 * {@link SelectionOptionVO} 列表，供 {@code SelectionResolver} 拼 {@code selection_request} 事件。
 *
 * <p>确定性卡片方案下，4 个 scenario 的候选数据都不再由大模型编排：
 * <ul>
 *   <li>{@code space_select} / {@code datasource_select}：由 {@code McpToolInvoker} 程序化调用
 *       {@code listOntologySpaces} / {@code searchDatasources} 取真实列表，本类做字段映射；</li>
 *   <li>{@code properties_select} / {@code relations_select}：从 SessionStateStore 持久化的
 *       {@code properties_proposed} / {@code relations_proposed} 状态块回查，本类做字段映射。</li>
 * </ul>
 * 每条选项都带 {@code payload}（原始字段对象透传），供前端渲染表格式向导卡片；{@code description} 作降级展示。
 */
@Component
@RequiredArgsConstructor
public class SelectionCardFactory {

    private static final String TITLE_SPACE = "请选择目标本体空间";
    private static final String TITLE_DATASOURCE = "请选择目标数据源";
    private static final String TITLE_PROPERTIES = "请勾选需要纳入本体的属性";
    private static final String TITLE_RELATIONS = "请勾选需要纳入本体的关系";

    private final ObjectMapper objectMapper;

    /** scenario 是否受支持。 */
    public static boolean supports(String scenario) {
        return "space_select".equals(scenario) || "datasource_select".equals(scenario)
                || "properties_select".equals(scenario) || "relations_select".equals(scenario);
    }

    /** scenario 对应卡片标题。 */
    public static String titleOf(String scenario) {
        switch (scenario) {
            case "space_select":
                return TITLE_SPACE;
            case "datasource_select":
                return TITLE_DATASOURCE;
            case "properties_select":
                return TITLE_PROPERTIES;
            case "relations_select":
                return TITLE_RELATIONS;
            default:
                return "请选择";
        }
    }

    /** scenario 是否多选：space/datasource 单选，properties/relations 多选。 */
    public static boolean multiSelectOf(String scenario) {
        return "properties_select".equals(scenario) || "relations_select".equals(scenario);
    }

    /** listOntologySpaces 返回（MCP 包裹或裸数组）→ 空间卡片选项。 */
    public List<SelectionOptionVO> spaceOptions(String rawToolOutput) throws Exception {
        JsonNode array = requireArray(rawToolOutput);
        List<SelectionOptionVO> options = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            String displayName = node.path("displayName").asText("");
            String apiName = node.path("apiName").asText("");
            String description = node.path("description").asText("");
            StringBuilder desc = new StringBuilder("apiName=").append(apiName)
                    .append("；本体数=").append(node.path("ontologyCount").asInt(0));
            if (!description.isBlank()) {
                desc.append("；").append(description);
            }
            options.add(SelectionOptionVO.builder()
                    .id(String.valueOf(node.path("spaceId").asInt()))
                    .label(displayName.isBlank() ? apiName : displayName)
                    .description(desc.toString())
                    .payload(toMap(node))
                    .build());
        }
        return options;
    }

    /** searchDatasources 返回（MCP 包裹或裸数组）→ 数据源卡片选项。 */
    public List<SelectionOptionVO> datasourceOptions(String rawToolOutput) throws Exception {
        JsonNode array = requireArray(rawToolOutput);
        List<SelectionOptionVO> options = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            String description = node.path("description").asText("");
            String desc = node.path("dbType").asText("") + "，" + node.path("host").asText("") + ":"
                    + node.path("port").asInt(0) + "/" + node.path("dbName").asText("");
            if (!description.isBlank()) {
                desc = desc + "；" + description;
            }
            options.add(SelectionOptionVO.builder()
                    .id(String.valueOf(node.path("id").asInt()))
                    .label(node.path("name").asText(""))
                    .description(desc)
                    .payload(toMap(node))
                    .build());
        }
        return options;
    }

    /** properties_proposed 状态块 → 属性多选卡片选项（id=field，重名附 #N，payload 透传原始属性对象）。 */
    public List<SelectionOptionVO> propertiesOptions(String proposedJson) throws Exception {
        JsonNode root = objectMapper.readTree(proposedJson);
        JsonNode properties = root.path("properties");
        List<SelectionOptionVO> options = new ArrayList<>();
        if (!properties.isArray()) {
            return options;
        }
        Set<String> usedIds = new HashSet<>();
        for (JsonNode p : properties) {
            String field = p.path("field").asText("");
            String name = p.path("name").asText("");
            String type = p.path("type").asText("");
            String sourceTable = p.path("sourceTable").asText("");
            String viaField = p.path("viaField").asText("");
            String description = p.path("description").asText("");
            boolean primaryKey = p.path("primaryKey").asBoolean(false);
            StringBuilder desc = new StringBuilder(type).append(" · ").append(sourceTable).append(" · ")
                    .append(primaryKey ? "主键" : (viaField.isBlank() ? "普通" : "关联·via " + viaField));
            if (!description.isBlank()) {
                desc.append(" · ").append(description);
            }
            options.add(SelectionOptionVO.builder()
                    .id(uniqueId(field.isBlank() ? name : field, usedIds))
                    .label(name.isBlank() ? field : name)
                    .description(desc.toString())
                    .payload(toMap(p))
                    .build());
        }
        return options;
    }

    /** relations_proposed 状态块 → 关系多选卡片选项（id=name，重名附 #N，payload 透传原始关系对象）。 */
    public List<SelectionOptionVO> relationsOptions(String proposedJson) throws Exception {
        JsonNode root = objectMapper.readTree(proposedJson);
        JsonNode relations = root.path("relations");
        List<SelectionOptionVO> options = new ArrayList<>();
        if (!relations.isArray()) {
            return options;
        }
        Set<String> usedIds = new HashSet<>();
        for (JsonNode r : relations) {
            String name = r.path("name").asText("");
            String desc = r.path("typeName").asText("") + " · 目标：" + r.path("targetDisplayName").asText("")
                    + " · 依据：" + r.path("reasoning").asText("");
            options.add(SelectionOptionVO.builder()
                    .id(uniqueId(name, usedIds))
                    .label(name)
                    .description(desc)
                    .payload(toMap(r))
                    .build());
        }
        return options;
    }

    /**
     * 解析为数据数组节点：兼容 MCP 包裹形态 {@code [{"text":"<内层数组>"}]} 与裸数组。
     * 非数组抛异常，由调用方处置。
     */
    private JsonNode requireArray(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("工具返回为空");
        }
        JsonNode root = objectMapper.readTree(raw.trim());
        if (!root.isArray()) {
            throw new IllegalArgumentException("返回值不是 JSON 数组");
        }
        if (root.size() == 1 && root.get(0).isObject() && root.get(0).has("text")) {
            JsonNode inner = objectMapper.readTree(root.get(0).get("text").asText());
            if (!inner.isArray()) {
                throw new IllegalArgumentException("内层不是 JSON 数组");
            }
            return inner;
        }
        return root;
    }

    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
        });
    }

    /** 保证同一次卡片内 id 唯一：重名时追加 #N（payload 内原始字段不受影响）。 */
    private String uniqueId(String base, Set<String> used) {
        String id = base;
        int n = 2;
        while (used.contains(id)) {
            id = base + "#" + n;
            n++;
        }
        used.add(id);
        return id;
    }
}
