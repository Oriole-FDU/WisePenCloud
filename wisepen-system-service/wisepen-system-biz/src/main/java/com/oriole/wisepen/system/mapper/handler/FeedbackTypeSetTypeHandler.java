package com.oriole.wisepen.system.mapper.handler;

import com.oriole.wisepen.system.api.enums.FeedbackType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MySQL SET 通过 JDBC 读写逗号分隔的枚举值，API 层使用类型安全的集合。
 */
public class FeedbackTypeSetTypeHandler extends BaseTypeHandler<Set<FeedbackType>> {

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Set<FeedbackType> parameter,
                                    JdbcType jdbcType) throws SQLException {
        statement.setString(index, parameter.stream().map(FeedbackType::getValue).collect(Collectors.joining(",")));
    }

    @Override
    public Set<FeedbackType> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public Set<FeedbackType> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public Set<FeedbackType> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private Set<FeedbackType> parse(String value) {
        if (value == null) return null;
        Set<FeedbackType> types = EnumSet.noneOf(FeedbackType.class);
        // 历史记录可能未选择类型，MySQL SET 的空字符串对应空集合。
        if (!value.isEmpty()) {
            for (String type : value.split(",")) {
                types.add(FeedbackType.valueOf(type));
            }
        }
        return types;
    }
}
