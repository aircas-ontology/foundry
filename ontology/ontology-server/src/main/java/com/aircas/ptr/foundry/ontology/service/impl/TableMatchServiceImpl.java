package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.ColumnMetaDTO;
import com.aircas.ptr.foundry.ontology.model.dto.ForeignKeyDTO;
import com.aircas.ptr.foundry.ontology.model.dto.LlmTableSelectionDTO;
import com.aircas.ptr.foundry.ontology.model.dto.TableMetaDTO;
import com.aircas.ptr.foundry.ontology.model.param.TableMatchParam;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.ColumnMatchVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableMatchResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableMatchVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.DatasourceConnectionMapper;
import com.aircas.ptr.foundry.ontology.service.DatasourceMetadataService;
import com.aircas.ptr.foundry.ontology.service.TableMatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 表智能匹配服务实现
 * <p>
 * 流程：
 * 1) 校验数据源存在且启用
 * 2) 查询目标库全部表（表名+注释）
 * 3) 组装提示词，调用 LLM 选出主表
 * 4) 查询该 schema 下全部外键，从主表出发双向扩展一层得到关联表
 * 5) 查询每张选中表的字段（含中文注释）+ 外键引用信息
 * 6) 拼装 VO 返回
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TableMatchServiceImpl implements TableMatchService {

    private static final Integer STATUS_ENABLED = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 从 LLM 返回文本中兜底提取 JSON 对象 */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    /** 从任意文本中兜底提取 mainTable 字段 */
    private static final Pattern MAIN_TABLE_PATTERN =
            Pattern.compile("\"mainTable\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REASONING_PATTERN =
            Pattern.compile("\"reasoning\"\\s*:\\s*\"([^\"]*)\"");

    /** LLM system prompt：让模型只输出 JSON，避免多余解释 */
    private static final String SYSTEM_PROMPT = """
            你是数据库结构分析专家。你的任务是根据用户的自然语言描述，从候选表清单中选出**唯一一张最匹配的主表**。
            严格遵守以下规则：
            1. 只能选择候选清单中出现过的表名，不要臆造。
            2. 输出必须是**单一 JSON 对象**，不要包裹 markdown 代码块，不要有任何解释性文字。
            3. JSON 结构固定为：{"mainTable":"表名","reasoning":"选择理由(50字内)"}
            4. 如果候选清单为空或用户描述完全不相关，返回：{"mainTable":"","reasoning":"未找到匹配表"}
            """;

    private final DatasourceConnectionMapper datasourceConnectionMapper;
    private final DatasourceMetadataService datasourceMetadataService;
    private final ChatClient chatClient;

    @Override
    public TableMatchResultVO predict(TableMatchParam param) {
        // 1. 加载并校验数据源
        DatasourceConnection conn = loadEnabledConnection(param.getDatasourceId());
        String schema = StringUtils.defaultIfBlank(conn.getSchemaName(), "public");

        // 2. 拉全部表元数据
        List<TableMetaDTO> allTables = datasourceMetadataService.listTables(conn, schema);
        if (CollectionUtils.isEmpty(allTables)) {
            throw new BusinessException(
                    "数据源 " + conn.getName() + " 的 schema [" + schema + "] 下没有任何表",
                    HttpStatus.BAD_REQUEST);
        }
        log.info("tableMatch: datasource={}, schema={}, tableCount={}",
                conn.getName(), schema, allTables.size());

        // 3. 让 LLM 挑主表
        LlmTableSelectionDTO selection = selectMainTableByLlm(param.getUserInput(), allTables);
        String mainTable = selection.getMainTable();
        if (StringUtils.isBlank(mainTable)) {
            return TableMatchResultVO.builder()
                    .userInput(param.getUserInput())
                    .datasourceId(conn.getId())
                    .datasourceName(conn.getName())
                    .reasoning(selection.getReasoning())
                    .tables(Collections.emptyList())
                    .build();
        }
        // LLM 有可能返回不在候选清单里的表名，做一次校验，防止后续查询报错
        TableMetaDTO mainMeta = allTables.stream()
                .filter(t -> mainTable.equalsIgnoreCase(t.getTableName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "LLM 返回的表 [" + mainTable + "] 不在数据源候选清单中，请重试或调整描述",
                        HttpStatus.INTERNAL_SERVER_ERROR));

        // 4. 外键扩展关联表（双向一层）
        List<ForeignKeyDTO> fks = datasourceMetadataService.listForeignKeys(conn, schema);
        Map<String, String> relatedTableRelations = expandRelatedTables(mainMeta.getTableName(), fks);

        // 5. 组装每张表的字段详情
        List<TableMatchVO> tableVOs = new ArrayList<>();
        tableVOs.add(buildTableVO(conn, schema, mainMeta, true, null, fks));
        for (Map.Entry<String, String> entry : relatedTableRelations.entrySet()) {
            String relatedTable = entry.getKey();
            String relation = entry.getValue();
            TableMetaDTO meta = allTables.stream()
                    .filter(t -> relatedTable.equalsIgnoreCase(t.getTableName()))
                    .findFirst()
                    .orElse(TableMetaDTO.builder()
                            .schemaName(schema).tableName(relatedTable).build());
            tableVOs.add(buildTableVO(conn, schema, meta, false, relation, fks));
        }

        return TableMatchResultVO.builder()
                .userInput(param.getUserInput())
                .datasourceId(conn.getId())
                .datasourceName(conn.getName())
                .reasoning(selection.getReasoning())
                .tables(tableVOs)
                .build();
    }

    // ------------------------------------------------------------------
    // 数据源加载
    // ------------------------------------------------------------------

    private DatasourceConnection loadEnabledConnection(Integer datasourceId) {
        PreconditionUtils.checkNotNull(datasourceId, "datasourceId 不能为空", HttpStatus.BAD_REQUEST);
        DatasourceConnection conn = datasourceConnectionMapper.selectById(datasourceId);
        PreconditionUtils.checkNotNull(conn,
                "数据源不存在: id=" + datasourceId, HttpStatus.NOT_FOUND);
        if (!STATUS_ENABLED.equals(conn.getStatus())) {
            throw new BusinessException(
                    "数据源 [" + conn.getName() + "] 已禁用，无法用于表匹配", HttpStatus.BAD_REQUEST);
        }
        return conn;
    }

    // ------------------------------------------------------------------
    // LLM 交互
    // ------------------------------------------------------------------

    private LlmTableSelectionDTO selectMainTableByLlm(String userInput, List<TableMetaDTO> tables) {
        String userPrompt = buildUserPrompt(userInput, tables);
        log.debug("tableMatch prompt: {}", userPrompt);

        String raw;
        try {
            raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("call LLM failed", e);
            throw new BusinessException("调用大模型失败: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
        log.info("tableMatch LLM raw response: {}", raw);
        return parseLlmResponse(raw, tables);
    }

    private String buildUserPrompt(String userInput, List<TableMetaDTO> tables) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户描述】\n").append(userInput).append("\n\n");
        sb.append("【候选表清单】（格式：序号. 表名 - 表中文描述）\n");
        int idx = 1;
        for (TableMetaDTO t : tables) {
            sb.append(idx++).append(". ")
                    .append(t.getTableName())
                    .append(" - ")
                    .append(StringUtils.defaultIfBlank(t.getTableComment(), "(无注释)"))
                    .append('\n');
        }
        sb.append("\n请选出最匹配的主表，按规定 JSON 格式返回。");
        return sb.toString();
    }

    /**
     * 解析 LLM 返回：先按严格 JSON 解析，失败则用正则兜底提取 mainTable / reasoning
     */
    private LlmTableSelectionDTO parseLlmResponse(String raw, List<TableMetaDTO> tables) {
        if (StringUtils.isBlank(raw)) {
            throw new BusinessException("大模型返回为空", HttpStatus.SERVICE_UNAVAILABLE);
        }
        // 优先严格解析
        String json = extractJsonBlock(raw);
        if (json != null) {
            try {
                LlmTableSelectionDTO dto = OBJECT_MAPPER.readValue(json, LlmTableSelectionDTO.class);
                if (dto != null && StringUtils.isNotBlank(dto.getMainTable())) {
                    return dto;
                }
            } catch (Exception ignore) {
                log.warn("strict JSON parse failed, fallback to regex. raw={}", raw);
            }
        }
        // 正则兜底
        String mainTable = matchFirst(MAIN_TABLE_PATTERN, raw);
        String reasoning = matchFirst(REASONING_PATTERN, raw);
        if (StringUtils.isBlank(mainTable)) {
            throw new BusinessException("大模型未按格式返回主表，请重试。raw=" + raw,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        // 大小写不敏感对齐候选清单中的实际表名
        String normalized = tables.stream()
                .map(TableMetaDTO::getTableName)
                .filter(n -> n.equalsIgnoreCase(mainTable))
                .findFirst()
                .orElse(mainTable);
        LlmTableSelectionDTO dto = new LlmTableSelectionDTO();
        dto.setMainTable(normalized);
        dto.setReasoning(reasoning);
        return dto;
    }

    private String extractJsonBlock(String raw) {
        String trimmed = raw.trim();
        // 去掉可能的 markdown 代码块
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        Matcher m = JSON_OBJECT_PATTERN.matcher(trimmed);
        return m.find() ? m.group() : null;
    }

    private String matchFirst(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------------
    // 外键扩展
    // ------------------------------------------------------------------

    /**
     * 从主表出发做一层双向外键扩展。
     * 返回有序 map：key = 关联表名，value = 关联关系可读描述。
     */
    private Map<String, String> expandRelatedTables(String mainTable, List<ForeignKeyDTO> fks) {
        Map<String, String> related = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(fks)) {
            return related;
        }
        // 去重：避免同一对表因多列外键被重复添加
        Set<String> seen = new HashSet<>();
        seen.add(mainTable.toLowerCase());

        for (ForeignKeyDTO fk : fks) {
            // 主表 -> 关联表（出向）
            if (mainTable.equalsIgnoreCase(fk.getFromTable())
                    && !mainTable.equalsIgnoreCase(fk.getToTable())) {
                String key = fk.getToTable();
                if (seen.add(key.toLowerCase())) {
                    related.put(key, fk.getFromTable() + "." + fk.getFromColumn()
                            + " -> " + fk.getToTable() + "." + fk.getToColumn());
                }
            }
            // 关联表 -> 主表（入向）
            else if (mainTable.equalsIgnoreCase(fk.getToTable())
                    && !mainTable.equalsIgnoreCase(fk.getFromTable())) {
                String key = fk.getFromTable();
                if (seen.add(key.toLowerCase())) {
                    related.put(key, fk.getFromTable() + "." + fk.getFromColumn()
                            + " -> " + fk.getToTable() + "." + fk.getToColumn());
                }
            }
        }
        return related;
    }

    // ------------------------------------------------------------------
    // VO 组装
    // ------------------------------------------------------------------

    private TableMatchVO buildTableVO(DatasourceConnection conn,
                                      String schema,
                                      TableMetaDTO meta,
                                      boolean isMain,
                                      String relation,
                                      List<ForeignKeyDTO> allFks) {
        List<ColumnMetaDTO> columns = datasourceMetadataService.listColumns(
                conn, schema, meta.getTableName());
        // 当前表涉及的外键，用于标记 foreignKey / refTable / refColumn
        Map<String, ForeignKeyDTO> outgoingFkByColumn = allFks.stream()
                .filter(fk -> meta.getTableName().equalsIgnoreCase(fk.getFromTable()))
                .collect(Collectors.toMap(
                        ForeignKeyDTO::getFromColumn,
                        fk -> fk,
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<ColumnMatchVO> columnVOs = columns.stream()
                .sorted(Comparator.comparing(c -> c.getOrdinal() == null ? 0 : c.getOrdinal()))
                .map(c -> {
                    ForeignKeyDTO fk = outgoingFkByColumn.get(c.getColumnName());
                    return ColumnMatchVO.builder()
                            .columnName(c.getColumnName())
                            .dataType(c.getDataType())
                            .description(c.getColumnComment())
                            .primaryKey(Boolean.TRUE.equals(c.getPrimaryKey()))
                            .foreignKey(fk != null)
                            .refTable(fk == null ? null : fk.getToTable())
                            .refColumn(fk == null ? null : fk.getToColumn())
                            .build();
                })
                .collect(Collectors.toList());

        return TableMatchVO.builder()
                .tableName(meta.getTableName())
                .schemaName(schema)
                .description(meta.getTableComment())
                .mainTable(isMain)
                .relation(relation)
                .columns(columnVOs)
                .build();
    }

    /**
     * 保留：便于将来做递归多层扩展时复用
     */
    @SuppressWarnings("unused")
    private Set<String> tableNamesLowerCase(List<TableMetaDTO> tables) {
        return new LinkedHashSet<>(tables.stream()
                .map(t -> t.getTableName().toLowerCase())
                .collect(Collectors.toSet()));
    }
}
