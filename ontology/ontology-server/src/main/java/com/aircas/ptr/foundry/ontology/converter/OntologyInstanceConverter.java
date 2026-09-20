package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyInstanceDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 数据湖行记录 -> ontology_instance ES 文档 转换器。
 */
public class OntologyInstanceConverter {

    /** 实例对象文档类型 */
    public static final String DOC_TYPE_INSTANCE = "ontology_instance";

    private OntologyInstanceConverter() {
    }

    /**
     * 组合文档唯一键：{schema}.{table_name}.{ontology_uid}.{主键列值}。
     * <p>
     * 加入 ontology_uid 是因为同一张物理表可能被多个本体（如继承、手工把不同本体主键属性
     * 绑到同一张表）当作主表，一行数据会派生出多篇"本体视角"的实例文档；若 pk 不含本体标识，
     * 这些文档 _id 相同会互相覆盖。含 ontology_uid 后每篇文档唯一。
     */
    public static String buildPk(String schema, String tableName, String ontologyUid, Object primaryKeyValue) {
        return schema + "." + tableName + "." + ontologyUid + "." + primaryKeyValue;
    }

    /**
     * 大小写不敏感地取行内列值。
     * <p>
     * PG 未加引号的标识符会折叠为小写，Debezium 输出的列名即小写；
     * 而 ontology_property.datasource_column_name 可能保留了建表时的大写写法，
     * 直接 {@code row.get(column)} 会取到 null 导致实例被静默跳过同步。
     */
    public static Object getColumnValue(Map<String, Object> row, String columnName) {
        if (row == null || StringUtils.isEmpty(columnName)) {
            return null;
        }
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (columnName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * @param schema           数据湖 schema（空间 api_name）
     * @param tableName        数据湖表名（datasource_id）
     * @param primaryKeyValue  行主键列值
     * @param titleValue       行名称键列值，可为空（为空时取主键值）
     * @param row              整行数据（列名 -> 值），用于拼接 search_text
     * @param meta             行所属本体（可为空，未绑定本体的表不写实例文档时由调用方保证）
     * @param space            本体所属空间（可为空）
     */
    public static EsOntologyInstanceDTO convert(String schema, String tableName, Object primaryKeyValue,
                                                Object titleValue, Map<String, Object> row,
                                                OntologyMeta meta, OntologySpace space) {
        if (StringUtils.isEmpty(schema) || StringUtils.isEmpty(tableName) || primaryKeyValue == null) {
            return null;
        }
        EsOntologyInstanceDTO dto = new EsOntologyInstanceDTO();
        dto.setPk(buildPk(schema, tableName, meta == null ? null : meta.getUniqueIdentifier(), primaryKeyValue));
        dto.setDocType(DOC_TYPE_INSTANCE);
        dto.setTableName(tableName);
        dto.setSchema(schema);

        // 名称键缺失或为空值时回退主键值，避免写入空 name 使名称检索/联想失效
        String name = titleValue == null || StringUtils.isBlank(titleValue.toString())
                ? primaryKeyValue.toString() : titleValue.toString();
        dto.setName(name);
        dto.setNameSuggest(List.of(name));
        dto.setSearchText(buildSearchText(row));

        if (meta != null) {
            dto.setOntologyUid(meta.getUniqueIdentifier());
            dto.setOntologyName(meta.getDisplayName());
            dto.setApiName(meta.getApiName());
        }
        if (space != null) {
            dto.setSpaceId(space.getId());
            dto.setSpaceDisplayName(space.getDisplayName());
            dto.setSpaceApiName(space.getApiName());
        }
        return dto;
    }

    /**
     * 将行内所有列值拼接为全文检索文本。
     */
    private static String buildSearchText(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(" ");
        row.values().forEach(v -> {
            String text = stringifyCellValue(v);
            if (StringUtils.isNotBlank(text)) {
                joiner.add(text);
            }
        });
        return joiner.length() == 0 ? null : joiner.toString();
    }

    /**
     * 列值转检索文本：jsonb/数组等复合值以 JSON 形式展开，避免 Java 集合字面量污染全文索引。
     */
    private static String stringifyCellValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof Collection) {
            return JSON.toJSONString(value);
        }
        return value.toString();
    }
}
