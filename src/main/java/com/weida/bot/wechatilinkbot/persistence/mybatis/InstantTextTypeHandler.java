package com.weida.bot.wechatilinkbot.persistence.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Stores {@link Instant} values as ISO-8601 UTC text for SQLite. */
public class InstantTextTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            Instant parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setString(index, parameter.toString());
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private static Instant parse(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
