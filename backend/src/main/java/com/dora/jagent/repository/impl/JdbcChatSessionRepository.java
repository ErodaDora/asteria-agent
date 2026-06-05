package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.ChatSession;
import com.dora.jagent.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcChatSessionRepository implements ChatSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<ChatSession> findByIdAndUserId(String sessionId, String userId) {
        String sql = """
                SELECT id, user_id, agent_id, title, summary, summary_updated_at, created_at, updated_at
                FROM chat_session
                WHERE id = CAST(? AS uuid) AND user_id = CAST(? AS uuid)
                """;
        List<ChatSession> sessions = jdbcTemplate.query(sql, new ChatSessionRowMapper(), sessionId, userId);
        return sessions.stream().findFirst();
    }

    @Override
    public Optional<ChatSession> findByIdAndUserIdAndAgentId(String sessionId, String userId, String agentId) {
        String sql = """
                SELECT id, user_id, agent_id, title, summary, summary_updated_at, created_at, updated_at
                FROM chat_session
                WHERE id = CAST(? AS uuid) AND user_id = CAST(? AS uuid) AND agent_id = CAST(? AS uuid)
                """;
        List<ChatSession> sessions = jdbcTemplate.query(sql, new ChatSessionRowMapper(), sessionId, userId, agentId);
        return sessions.stream().findFirst();
    }

    @Override
    public List<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId) {
        String sql = """
                SELECT id, user_id, agent_id, title, summary, summary_updated_at, created_at, updated_at
                FROM chat_session
                WHERE user_id = CAST(? AS uuid)
                  AND agent_id IS NULL
                ORDER BY updated_at DESC
                """;
        return jdbcTemplate.query(sql, new ChatSessionRowMapper(), userId);
    }

    @Override
    public List<ChatSession> findByUserIdAndAgentIdOrderByUpdatedAtDesc(String userId, String agentId) {
        String sql = """
                SELECT id, user_id, agent_id, title, summary, summary_updated_at, created_at, updated_at
                FROM chat_session
                WHERE user_id = CAST(? AS uuid)
                  AND agent_id = CAST(? AS uuid)
                ORDER BY updated_at DESC
                """;
        return jdbcTemplate.query(sql, new ChatSessionRowMapper(), userId, agentId);
    }

    @Override
    public ChatSession save(ChatSession chatSession) {
        String sql = """
                INSERT INTO chat_session (id, user_id, agent_id, title, summary, summary_updated_at, created_at, updated_at)
                VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                chatSession.getId(),
                chatSession.getUserId(),
                chatSession.getAgentId(),
                chatSession.getTitle(),
                chatSession.getSummary(),
                chatSession.getSummaryUpdatedAt() == null ? null : Timestamp.valueOf(chatSession.getSummaryUpdatedAt()),
                Timestamp.valueOf(chatSession.getCreatedAt()),
                Timestamp.valueOf(chatSession.getUpdatedAt())
        );
        return chatSession;
    }

    @Override
    public ChatSession update(ChatSession chatSession) {
        String sql = """
                UPDATE chat_session
                SET title = ?, summary = ?, summary_updated_at = ?, updated_at = ?
                WHERE id = CAST(? AS uuid)
                """;
        jdbcTemplate.update(
                sql,
                chatSession.getTitle(),
                chatSession.getSummary(),
                chatSession.getSummaryUpdatedAt() == null ? null : Timestamp.valueOf(chatSession.getSummaryUpdatedAt()),
                Timestamp.valueOf(chatSession.getUpdatedAt()),
                chatSession.getId()
        );
        return chatSession;
    }

    @Override
    public void deleteById(String sessionId) {
        String sql = """
                DELETE FROM chat_session
                WHERE id = CAST(? AS uuid)
                """;
        jdbcTemplate.update(sql, sessionId);
    }

    private static class ChatSessionRowMapper implements RowMapper<ChatSession> {
        @Override
        public ChatSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            return ChatSession.builder()
                    .id(rs.getString("id"))
                    .userId(rs.getString("user_id"))
                    .agentId(rs.getString("agent_id"))
                    .title(rs.getString("title"))
                    .summary(rs.getString("summary"))
                    .summaryUpdatedAt(rs.getTimestamp("summary_updated_at") == null ? null : rs.getTimestamp("summary_updated_at").toLocalDateTime())
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
