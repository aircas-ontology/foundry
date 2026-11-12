package com.aircas.ptr.foundry.ontology.repository.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * TEXT 列存逗号分隔的字符串数组：行为挂载对象id 这类「一个对象多个值」的列用。
 * <p>写入时空列表写成空串，读取时按逗号切分、去空白、去重；空串和 NULL 都读成空列表。</p>
 */
public class StringListCommaTypeHandler extends BaseTypeHandler<List<String>> {

    private static final String SEPARATOR = ",";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, String.join(SEPARATOR, parameter));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return split(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return split(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return split(cs.getString(columnIndex));
    }

    private List<String> split(String value) {
        var identifiers = new LinkedHashSet<String>();
        if (value != null) {
            for (var part : value.split(SEPARATOR)) {
                var trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    identifiers.add(trimmed);
                }
            }
        }
        return new ArrayList<>(identifiers);
    }
}
