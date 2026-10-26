package com.aircas.ptr.foundry.ontology.repository.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Maps a {@link Date} property to a SQL timestamp without discarding its
 * fractional-second precision when the JDBC driver returns a {@link Timestamp}.
 *
 * <p>MyBatis' default {@code DateTypeHandler} converts a timestamp to a new
 * {@code Date}, which keeps only milliseconds. Returning the JDBC timestamp
 * directly is safe because {@code Timestamp} extends {@code Date}, and allows
 * callers that use the value as an optimistic-lock token to retain microseconds.</p>
 */
public class PreciseDateTypeHandler extends BaseTypeHandler<Date> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Date parameter, JdbcType jdbcType)
            throws SQLException {
        Timestamp timestamp = parameter instanceof Timestamp
                ? (Timestamp) parameter
                : new Timestamp(parameter.getTime());
        ps.setTimestamp(i, timestamp);
    }

    @Override
    public Date getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getTimestamp(columnName);
    }

    @Override
    public Date getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getTimestamp(columnIndex);
    }

    @Override
    public Date getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getTimestamp(columnIndex);
    }
}
