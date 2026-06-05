package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.User;
import com.dora.jagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

// @Repository 表示这是持久化层组件。
// 这一版直接用 JdbcTemplate，先把最小数据库操作走通。
@Repository
@RequiredArgsConstructor
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<User> findById(String id) {
        String sql = """
                SELECT id, email, password_hash, display_name, created_at, updated_at
                FROM app_user
                WHERE id = CAST(? AS uuid)
                """;
        List<User> users = jdbcTemplate.query(sql, new UserRowMapper(), id);
        return users.stream().findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = """
                SELECT id, email, password_hash, display_name, created_at, updated_at
                FROM app_user
                WHERE email = ?
                """;
        List<User> users = jdbcTemplate.query(sql, new UserRowMapper(), email);
        return users.stream().findFirst();
    }

    @Override
    public User save(User user) {
        String sql = """
                INSERT INTO app_user (id, email, password_hash, display_name, created_at, updated_at)
                VALUES (CAST(? AS uuid), ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getDisplayName(),
                Timestamp.valueOf(user.getCreatedAt()),
                Timestamp.valueOf(user.getUpdatedAt())
        );
        return user;
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            return User.builder()
                    .id(rs.getString("id"))
                    .email(rs.getString("email"))
                    .passwordHash(rs.getString("password_hash"))
                    .displayName(rs.getString("display_name"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
