package com.aircas.ptr.foundry.ontology.entity.service;


import com.aircas.ptr.foundry.ontology.entity.exception.ResourceNotFoundException;
import com.aircas.ptr.foundry.ontology.entity.model.dto.FieldDTO;
import com.aircas.ptr.foundry.ontology.entity.model.dto.TableCreateDTO;
import com.aircas.ptr.foundry.ontology.entity.model.dto.TableQueryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

@Service
public class PostgresService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public void createTable(TableCreateDTO tableInfo) {
        // 检查表是否已存在
        if (isTableExists(tableInfo.getTableName())) {
            throw new RuntimeException("表 " + tableInfo.getTableName() + " 已存在");
        }

        // 构建创建表的SQL
        StringBuilder createTableSql = new StringBuilder();
        createTableSql.append("CREATE TABLE ").append(tableInfo.getTableName()).append(" (");

        // 添加字段定义
        List<String> columnDefinitions = new ArrayList<>();
        for (FieldDTO field : tableInfo.getFields()) {
            StringBuilder columnDef = new StringBuilder();
            columnDef.append(field.getFieldName())
                    .append(" ")
                    .append(field.getFieldType());

            if (field.isPrimaryKey()) {
                columnDef.append(" PRIMARY KEY");
            }

            if (!field.isNullable()) {
                columnDef.append(" NOT NULL");
            }

            columnDefinitions.add(columnDef.toString());
        }

        createTableSql.append(String.join(", ", columnDefinitions));
        createTableSql.append(")");

        // 执行创建表
        jdbcTemplate.execute(createTableSql.toString());

        // 添加表注释和字段注释
        addTableAndColumnComments(tableInfo);
    }

    private void addTableAndColumnComments(TableCreateDTO tableInfo) {
        // 添加表注释
        if (tableInfo.getTableComment() != null && !tableInfo.getTableComment().isEmpty()) {
            String tableCommentSql = String.format(
                    "COMMENT ON TABLE %s IS '%s'",
                    tableInfo.getTableName(),
                    tableInfo.getTableComment().replace("'", "''")
            );
            jdbcTemplate.execute(tableCommentSql);
        }

        // 添加字段注释
        for (FieldDTO field : tableInfo.getFields()) {
            if (field.getFieldComment() != null && !field.getFieldComment().isEmpty()) {
                String columnCommentSql = String.format(
                        "COMMENT ON COLUMN %s.%s IS '%s'",
                        tableInfo.getTableName(),
                        field.getFieldName(),
                        field.getFieldComment().replace("'", "''")
                );
                jdbcTemplate.execute(columnCommentSql);
            }
        }
    }

    public boolean isTableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ? AND table_schema = 'public'";
        int count = jdbcTemplate.queryForObject(sql, Integer.class, tableName.toLowerCase());
        return count > 0;
    }

    public Map<String, Object> getTableInfo(String tableName) {
        if (!isTableExists(tableName)) {
            throw new RuntimeException("表 " + tableName + " 不存在");
        }

        // 获取表结构信息
        String sql = "SELECT " +
                "c.column_name, " +
                "c.data_type, " +
                "c.is_nullable, " +
                "c.column_default, " +
                "pgd.description as column_comment, " +
                "(SELECT pg_catalog.obj_description(pgc.oid, 'pg_class') " +
                "FROM pg_catalog.pg_class pgc " +
                "WHERE pgc.relname = c.table_name) as table_comment " +
                "FROM information_schema.columns c " +
                "LEFT JOIN pg_catalog.pg_description pgd ON " +
                "pgd.objoid = (SELECT oid FROM pg_catalog.pg_class WHERE relname = c.table_name) " +
                "AND pgd.objsubid = c.ordinal_position " +
                "WHERE c.table_name = ? " +
                "ORDER BY c.ordinal_position";

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName.toLowerCase());

        // 获取主键信息
        String pkSql = "SELECT c.column_name " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.constraint_column_usage AS ccu USING (constraint_schema, constraint_name) " +
                "JOIN information_schema.columns AS c ON c.table_schema = tc.constraint_schema " +
                "AND tc.table_name = c.table_name AND ccu.column_name = c.column_name " +
                "WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_name = ?";

        List<String> primaryKeys = jdbcTemplate.queryForList(pkSql, String.class, tableName.toLowerCase());

        // 替换 Map.of 的写法
        Map<String, Object> result = new HashMap<>();
        result.put("tableName", tableName);
        result.put("columns", columns);
        result.put("primaryKeys", primaryKeys);
        return result;
    }

    @Transactional
    public void alterTable(String tableName, List<FieldDTO> addColumns,
                           List<String> dropColumns, List<FieldDTO> modifyColumns) {
        if (!isTableExists(tableName)) {
            throw new RuntimeException("表 " + tableName + " 不存在");
        }

        // 添加新列
        for (FieldDTO column : addColumns) {
            String addColumnSql = String.format("ALTER TABLE %s ADD COLUMN %s %s",
                    tableName, column.getFieldName(), column.getFieldType());
            if (!column.isNullable()) {
                addColumnSql += " NOT NULL";
            }
            jdbcTemplate.execute(addColumnSql);

            // 添加列注释
            if (column.getFieldComment() != null && !column.getFieldComment().isEmpty()) {
                String commentSql = String.format(
                        "COMMENT ON COLUMN %s.%s IS '%s'",
                        tableName,
                        column.getFieldName(),
                        column.getFieldComment().replace("'", "''")
                );
                jdbcTemplate.execute(commentSql);
            }
        }

        // 删除列
        for (String columnName : dropColumns) {
            String dropColumnSql = String.format("ALTER TABLE %s DROP COLUMN %s",
                    tableName, columnName);
            jdbcTemplate.execute(dropColumnSql);
        }

        // 修改列
        for (FieldDTO column : modifyColumns) {
            String modifyColumnSql = String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s",
                    tableName, column.getFieldName(), column.getFieldType());
            jdbcTemplate.execute(modifyColumnSql);

            // 修改是否可为空
            String nullableSql = String.format("ALTER TABLE %s ALTER COLUMN %s %s NULL",
                    tableName, column.getFieldName(), column.isNullable() ? "DROP NOT" : "SET NOT");
            jdbcTemplate.execute(nullableSql);

            // 更新列注释
            if (column.getFieldComment() != null) {
                String commentSql = String.format(
                        "COMMENT ON COLUMN %s.%s IS '%s'",
                        tableName,
                        column.getFieldName(),
                        column.getFieldComment().replace("'", "''")
                );
                jdbcTemplate.execute(commentSql);
            }
        }
    }

    @Transactional
    public void dropTable(String tableName) {
        if (!isTableExists(tableName)) {
            throw new RuntimeException("表 " + tableName + " 不存在");
        }

        String sql = "DROP TABLE " + tableName;
        jdbcTemplate.execute(sql);
    }

    public List<Map<String, Object>> queryTable(String tableName, TableQueryDTO queryDTO) {
        if (!isTableExists(tableName)) {
            throw new RuntimeException("表 " + tableName + " 不存在");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName).append(" WHERE 1=1");

        List<Object> params = new ArrayList<>();

        for (TableQueryDTO.FieldCondition condition : queryDTO.getConditions()) {
            sql.append(" AND ");

            switch (condition.getQueryType()) {
                case EQUAL:
                    if (condition.isExactMatch()) {
                        sql.append(condition.getFieldName()).append(" = ?");
                        params.add(condition.getValue());
                    } else {
                        sql.append(condition.getFieldName()).append(" ILIKE ?");
                        params.add("%" + condition.getValue() + "%");
                    }
                    break;

                case LIKE:
                    sql.append(condition.getFieldName()).append(" ILIKE ?");
                    params.add("%" + condition.getValue() + "%");
                    break;

                case RANGE:
                    sql.append(condition.getFieldName()).append(" BETWEEN ? AND ?");
                    params.add(condition.getValue());
                    params.add(condition.getEndValue());
                    break;
            }
        }

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 获取表中的数据行数
     *
     * @param tableName 表名
     * @return 表中的行数
     */
    @SuppressWarnings("null")
    public long getTableRowCount(String tableName) {
        // 检查表是否存在
        if (!isTableExists(tableName)) {
            throw new ResourceNotFoundException("表 " + tableName + " 不存在");
        }

        // 构建计数SQL
        String sql = "SELECT COUNT(*) FROM " + tableName;

        // 执行查询并返回结果
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    /**
     * 批量插入数据到指定表
     *
     * @param tableName 表名
     * @param dataList  数据对象列表
     * @return 成功插入的记录数
     */
    public int batchInsertData(String tableName, List<Map<String, Object>> dataList) {
        // 检查表是否存在
        if (!isTableExists(tableName)) {
            throw new ResourceNotFoundException("表 " + tableName + " 不存在");
        }

        if (dataList == null || dataList.isEmpty()) {
            return 0;
        }

        // 获取表结构信息
        Map<String, Object> tableInfo = getTableInfo(tableName);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columnsInfo = (List<Map<String, Object>>) tableInfo.get("columns");

        // 创建字段名到数据类型的映射
        Map<String, String> columnTypeMap = new HashMap<>();
        for (Map<String, Object> column : columnsInfo) {
            String columnName = (String) column.get("column_name");
            String dataType = (String) column.get("data_type");
            columnTypeMap.put(columnName.toLowerCase(), dataType.toLowerCase());
        }

        // 获取第一条数据的所有列
        Map<String, Object> firstRow = dataList.get(0);
        List<String> columns = new ArrayList<>(firstRow.keySet());

        // 对每条数据进行类型转换
        List<Map<String, Object>> convertedDataList = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            Map<String, Object> convertedData = new HashMap<>();
            for (String column : columns) {
                String columnLower = column.toLowerCase();
                if (columnTypeMap.containsKey(columnLower)) {
                    convertedData.put(column, convertValueToType(data.get(column), columnTypeMap.get(columnLower)));
                } else {
                    convertedData.put(column, data.get(column));
                }
            }
            convertedDataList.add(convertedData);
        }

        // 构建SQL语句
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");

        // 添加列名
        for (int i = 0; i < columns.size(); i++) {
            sql.append(columns.get(i));
            if (i < columns.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(") VALUES (");

        // 添加占位符
        for (int i = 0; i < columns.size(); i++) {
            sql.append("?");
            if (i < columns.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(")");

        // 使用BatchPreparedStatementSetter执行批量插入
        int[] results = jdbcTemplate.batchUpdate(sql.toString(), new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map<String, Object> row = convertedDataList.get(i);
                for (int j = 0; j < columns.size(); j++) {
                    ps.setObject(j + 1, row.get(columns.get(j)));
                }
            }

            @Override
            public int getBatchSize() {
                return convertedDataList.size();
            }
        });

        // 返回成功插入的记录总数
        return Arrays.stream(results).sum();
    }

    /**
     * 将值转换为指定的PostgreSQL数据类型
     *
     * @param value  原始值
     * @param pgType PostgreSQL数据类型
     * @return 转换后的值
     */
    private Object convertValueToType(Object value, String pgType) {
        if (value == null) {
            return null;
        }

        // 如果值已经是字符串以外的类型，且不是要转为JSON或JSONB，则不做转换
        if (!(value instanceof String) &&
                !pgType.equals("json") && !pgType.equals("jsonb")) {
            return value;
        }

        String strValue = value.toString().trim();

        try {
            switch (pgType) {
                case "integer":
                case "int":
                case "int4":
                    return Integer.parseInt(strValue);

                case "bigint":
                case "int8":
                    return Long.parseLong(strValue);

                case "numeric":
                case "decimal":
                    return new java.math.BigDecimal(strValue);

                case "real":
                case "float4":
                    return Float.parseFloat(strValue);

                case "double precision":
                case "float8":
                    return Double.parseDouble(strValue);

                case "boolean":
                case "bool":
                    if (strValue.equalsIgnoreCase("true") || strValue.equals("1") ||
                            strValue.equalsIgnoreCase("yes") || strValue.equalsIgnoreCase("y")) {
                        return true;
                    } else if (strValue.equalsIgnoreCase("false") || strValue.equals("0") ||
                            strValue.equalsIgnoreCase("no") || strValue.equalsIgnoreCase("n")) {
                        return false;
                    }
                    return Boolean.parseBoolean(strValue);

                case "date":
                    // 尝试转换为java.sql.Date
                    if (strValue.length() == 10 && strValue.contains("-")) {
                        return java.sql.Date.valueOf(strValue);
                    }
                    break;

                case "time":
                case "time without time zone":
                    // 尝试转换为java.sql.Time
                    if (strValue.length() >= 5 && strValue.contains(":")) {
                        return java.sql.Time.valueOf(strValue);
                    }
                    break;

                case "timestamp":
                case "timestamp without time zone":
                    // 尝试转换为java.sql.Timestamp
                    return java.sql.Timestamp.valueOf(strValue);

                case "json":
                case "jsonb":
                    // 只做简单验证，确保是有效的JSON字符串
                    if (strValue.startsWith("{") && strValue.endsWith("}") ||
                            strValue.startsWith("[") && strValue.endsWith("]")) {
                        return strValue;
                    }
                    break;

                // 其他类型保持原样
                default:
                    return value;
            }
        } catch (Exception e) {
            // 转换失败则返回原值
            return value;
        }

        return value;
    }
} 