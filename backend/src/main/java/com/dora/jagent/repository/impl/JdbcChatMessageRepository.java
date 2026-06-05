package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.ChatMessage;
import com.dora.jagent.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcChatMessageRepository implements ChatMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        String sql = """
                INSERT INTO chat_message (id, session_id, role, content, created_at)
                VALUES (CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                chatMessage.getId(),
                chatMessage.getSessionId(),
                chatMessage.getRole(),
                chatMessage.getContent(),
                Timestamp.valueOf(chatMessage.getCreatedAt())
        );
        return chatMessage;
    }

    @Override
    public List<ChatMessage> findBySessionId(String sessionId) {
        String sql = """
                SELECT id, session_id, role, content, created_at
                FROM chat_message
                WHERE session_id = CAST(? AS uuid)
                ORDER BY created_at ASC
                """;
        return jdbcTemplate.query(sql, new ChatMessageRowMapper(), sessionId);
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        String sql = """
                DELETE FROM chat_message
                WHERE session_id = CAST(? AS uuid)
                """;
        jdbcTemplate.update(sql, sessionId);
    }

    private static class ChatMessageRowMapper implements RowMapper<ChatMessage> {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            return ChatMessage.builder()
                    .id(rs.getString("id"))
                    .sessionId(rs.getString("session_id"))
                    .role(rs.getString("role"))
                    .content(rs.getString("content"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .build();
        }
    }
}
