package com.aiplatform.rag.vector;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.rag.config.RagVectorProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * PostgreSQL + PGVector 向量存储。
 *
 * 演示平台允许不同 Embedding 模型并存，因此使用无固定维度的 vector 列，
 * 并在查询时强制过滤 embedding_model_id 与 dimension，避免不同维度向量混用。
 * 数据量较大时应改为按维度固定列并创建 HNSW/IVFFlat 索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagVectorStore {

    private final RagVectorProperties properties;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private String tableName;
    private volatile boolean ready;

    @PostConstruct
    void initialize() {
        if (!properties.getTable().matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("PGVector 表名只能包含字母、数字与下划线");
        }
        tableName = properties.getTable();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(Math.max(2, properties.getPoolSize()));
        config.setMinimumIdle(1);
        config.setPoolName("ai-rag-pgvector");
        config.setConnectionTimeout(5000);
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("ApplicationName", "ai-platform-rag");
        try {
            dataSource = new HikariDataSource(config);
            jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        chunk_id           BIGINT PRIMARY KEY,
                        knowledge_base_id  BIGINT NOT NULL,
                        document_id        BIGINT NOT NULL,
                        embedding_model_id BIGINT NOT NULL,
                        embedding_dim      INT NOT NULL,
                        embedding          vector NOT NULL,
                        created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """.formatted(tableName));
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_%s_scope
                    ON %s (knowledge_base_id, embedding_model_id, embedding_dim)
                    """.formatted(tableName, tableName));
            Integer version = jdbcTemplate.queryForObject(
                    "SELECT 1", Integer.class);
            ready = version != null;
            log.info("PGVector initialized: table={}, pool={}", tableName,
                    dataSource.getMaximumPoolSize());
        } catch (Exception ex) {
            ready = false;
            log.warn("PGVector 未就绪，RAG 检索与向量化将返回明确错误：{}", rootMessage(ex));
        }
    }

    @PreDestroy
    void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public boolean isReady() {
        return ready;
    }

    public void ensureReady() {
        if (!ready) {
            throw new BusinessException(ErrorCode.RAG_UNAVAILABLE,
                    "PGVector 未就绪，请检查 PostgreSQL 与 pgvector 扩展配置");
        }
    }

    public void upsert(List<VectorRecord> records) {
        ensureReady();
        if (records == null || records.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO %s (chunk_id, knowledge_base_id, document_id, embedding_model_id,
                                embedding_dim, embedding)
                VALUES (?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (chunk_id) DO UPDATE SET
                    knowledge_base_id = EXCLUDED.knowledge_base_id,
                    document_id = EXCLUDED.document_id,
                    embedding_model_id = EXCLUDED.embedding_model_id,
                    embedding_dim = EXCLUDED.embedding_dim,
                    embedding = EXCLUDED.embedding
                """.formatted(tableName);
        try {
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    VectorRecord record = records.get(i);
                    ps.setLong(1, record.chunkId());
                    ps.setLong(2, record.knowledgeBaseId());
                    ps.setLong(3, record.documentId());
                    ps.setLong(4, record.embeddingModelId());
                    ps.setInt(5, record.vector().length);
                    ps.setString(6, toVectorLiteral(record.vector()));
                }

                @Override
                public int getBatchSize() {
                    return records.size();
                }
            });
        } catch (Exception ex) {
            throw unavailable("写入向量失败", ex);
        }
    }

    public List<ScoredChunkId> search(Long knowledgeBaseId, Long embeddingModelId,
                                      float[] queryVector, int topK) {
        ensureReady();
        String literal = toVectorLiteral(queryVector);
        String sql = """
                SELECT chunk_id, embedding <=> ?::vector AS distance
                FROM %s
                WHERE knowledge_base_id = ?
                  AND embedding_model_id = ?
                  AND embedding_dim = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.formatted(tableName);
        try {
            return jdbcTemplate.query(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, literal);
                ps.setLong(2, knowledgeBaseId);
                ps.setLong(3, embeddingModelId);
                ps.setInt(4, queryVector.length);
                ps.setString(5, literal);
                ps.setInt(6, topK);
                return ps;
            }, (rs, rowNum) -> new ScoredChunkId(rs.getLong("chunk_id"), rs.getDouble("distance")));
        } catch (Exception ex) {
            throw unavailable("向量检索失败", ex);
        }
    }

    public void deleteByDocument(Long documentId) {
        if (!ready) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE document_id = ?", documentId);
        } catch (Exception ex) {
            log.warn("Failed to delete vectors for document {}: {}", documentId, rootMessage(ex));
        }
    }

    public void deleteByKnowledgeBase(Long knowledgeBaseId) {
        if (!ready) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE knowledge_base_id = ?", knowledgeBaseId);
        } catch (Exception ex) {
            log.warn("Failed to delete vectors for knowledge base {}: {}", knowledgeBaseId, rootMessage(ex));
        }
    }

    public long countByKnowledgeBase(Long knowledgeBaseId) {
        if (!ready) {
            return 0;
        }
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE knowledge_base_id = ?",
                    Long.class, knowledgeBaseId);
            return count == null ? 0 : count;
        } catch (Exception ex) {
            return 0;
        }
    }

    private BusinessException unavailable(String action, Exception ex) {
        log.warn("{}: {}", action, rootMessage(ex));
        return new BusinessException(ErrorCode.RAG_UNAVAILABLE,
                action + "：PGVector 不可用，请检查数据库连接");
    }

    private String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw BusinessException.badRequest("Embedding 向量为空");
        }
        StringBuilder builder = new StringBuilder(vector.length * 10 + 2);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            float value = vector[i];
            if (!Float.isFinite(value)) {
                throw BusinessException.badRequest("Embedding 向量包含非法数值");
            }
            if (i > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        builder.append(']');
        return builder.toString();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current.getCause() != null && depth < 5) {
            current = current.getCause();
            depth++;
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    public record VectorRecord(
            Long chunkId,
            Long knowledgeBaseId,
            Long documentId,
            Long embeddingModelId,
            float[] vector
    ) {
    }

    public record ScoredChunkId(Long chunkId, double distance) {
    }
}
