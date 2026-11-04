package com.aiplatform.monitor.service.impl;

import com.aiplatform.common.api.PageResult;
import com.aiplatform.monitor.dto.CallLogQuery;
import com.aiplatform.monitor.service.MonitorService;
import com.aiplatform.monitor.vo.CallLogVO;
import com.aiplatform.monitor.vo.MonitorOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public MonitorOverviewVO overview(int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 30));
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        Timestamp startTimestamp = Timestamp.valueOf(start);

        Map<String, Object> aggregate = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total_calls,
                       COALESCE(SUM(data_source = 'REAL'), 0) AS real_calls,
                       COALESCE(SUM(data_source = 'SIMULATED'), 0) AS simulated_calls,
                       COALESCE(SUM(success = 1), 0) AS success_calls,
                       AVG(latency_ms) AS avg_latency,
                       COALESCE(SUM(COALESCE(total_tokens, 0)), 0) AS total_tokens,
                       SUM(estimated_cost) AS known_cost,
                       COALESCE(SUM(estimated_cost IS NULL), 0) AS unknown_cost_calls
                FROM ai_model_call_log
                WHERE created_at >= ?
                """, startTimestamp);

        long total = number(aggregate.get("total_calls"));
        long success = number(aggregate.get("success_calls"));
        double successRate = total == 0 ? 0 : round(success * 100.0 / total, 1);
        Long avgLatency = aggregate.get("avg_latency") == null
                ? null : Math.round(((Number) aggregate.get("avg_latency")).doubleValue());
        BigDecimal knownCost = aggregate.get("known_cost") instanceof BigDecimal value ? value : null;

        long agentExecutions = count("SELECT COUNT(*) FROM ai_agent_execution WHERE created_at >= ?", startTimestamp);
        long toolCalls = count("SELECT COUNT(*) FROM ai_tool_execution_log WHERE created_at >= ?", startTimestamp);
        long ragQueries = count("SELECT COUNT(*) FROM ai_rag_query_log WHERE created_at >= ?", startTimestamp);
        long multimodalTasks = count("SELECT COUNT(*) FROM ai_multimodal_task WHERE created_at >= ?", startTimestamp);
        long activeModels = count("SELECT COUNT(*) FROM ai_model_config WHERE enabled = 1 AND deleted = 0");
        long enabledAgents = count("SELECT COUNT(*) FROM ai_agent WHERE enabled = 1 AND deleted = 0");
        long enabledTools = count("SELECT COUNT(*) FROM ai_tool WHERE enabled = 1");
        long knowledgeBases = count("SELECT COUNT(*) FROM ai_knowledge_base WHERE deleted = 0");
        long knowledgeDocuments = count("SELECT COUNT(*) FROM ai_knowledge_document");
        long knowledgeChunks = count("SELECT COUNT(*) FROM ai_knowledge_chunk");

        var summary = new MonitorOverviewVO.Summary(
                total,
                number(aggregate.get("real_calls")),
                number(aggregate.get("simulated_calls")),
                success,
                total - success,
                successRate,
                avgLatency,
                number(aggregate.get("total_tokens")),
                knownCost,
                number(aggregate.get("unknown_cost_calls")),
                agentExecutions,
                toolCalls,
                ragQueries,
                multimodalTasks,
                activeModels,
                enabledAgents,
                enabledTools,
                knowledgeBases,
                knowledgeDocuments,
                knowledgeChunks
        );

        List<MonitorOverviewVO.TypeStat> types = jdbcTemplate.query("""
                        SELECT request_type, COUNT(*) AS calls, COALESCE(SUM(success = 1), 0) AS success_calls
                        FROM ai_model_call_log
                        WHERE created_at >= ?
                        GROUP BY request_type
                        ORDER BY calls DESC
                        """, (rs, rowNum) -> new MonitorOverviewVO.TypeStat(
                        rs.getString("request_type"),
                        rs.getLong("calls"),
                        rs.getLong("success_calls")),
                startTimestamp);

        List<MonitorOverviewVO.ModelStat> models = jdbcTemplate.query("""
                        SELECT COALESCE(model_name, '未知模型') AS model_name,
                               COUNT(*) AS calls,
                               COALESCE(SUM(COALESCE(total_tokens, 0)), 0) AS total_tokens,
                               AVG(latency_ms) AS avg_latency,
                               COALESCE(SUM(success = 1), 0) AS success_calls
                        FROM ai_model_call_log
                        WHERE created_at >= ?
                        GROUP BY model_name
                        ORDER BY calls DESC
                        LIMIT 6
                        """, (rs, rowNum) -> {
                    long calls = rs.getLong("calls");
                    long successCalls = rs.getLong("success_calls");
                    return new MonitorOverviewVO.ModelStat(
                            rs.getString("model_name"),
                            calls,
                            rs.getLong("total_tokens"),
                            rs.getObject("avg_latency") == null
                                    ? null : Math.round(rs.getDouble("avg_latency")),
                            calls == 0 ? 0 : round(successCalls * 100.0 / calls, 1)
                    );
                }, startTimestamp);

        Map<LocalDate, MonitorOverviewVO.TrendPoint> points = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        SELECT DATE(created_at) AS day,
                               COUNT(*) AS calls,
                               COALESCE(SUM(success = 1), 0) AS success_calls,
                               COALESCE(SUM(COALESCE(total_tokens, 0)), 0) AS total_tokens,
                               AVG(latency_ms) AS avg_latency
                        FROM ai_model_call_log
                        WHERE created_at >= ?
                        GROUP BY DATE(created_at)
                        ORDER BY day
                        """, rs -> {
                    LocalDate day = rs.getDate("day").toLocalDate();
                    points.put(day, new MonitorOverviewVO.TrendPoint(
                            day.format(DAY_LABEL),
                            rs.getLong("calls"),
                            rs.getLong("success_calls"),
                            rs.getLong("total_tokens"),
                            rs.getObject("avg_latency") == null
                                    ? null : Math.round(rs.getDouble("avg_latency"))
                    ));
                }, startTimestamp);
        List<MonitorOverviewVO.TrendPoint> trend = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = startDate.plusDays(offset);
            trend.add(points.getOrDefault(date,
                    new MonitorOverviewVO.TrendPoint(date.format(DAY_LABEL), 0, 0, 0, null)));
        }

        List<CallLogVO> recentCalls = jdbcTemplate.query("""
                        SELECT *
                        FROM ai_model_call_log
                        WHERE created_at >= ?
                        ORDER BY id DESC
                        LIMIT 8
                        """, this::mapCall, startTimestamp);

        return new MonitorOverviewVO(summary, trend, types, models, recentCalls);
    }

    @Override
    public PageResult<CallLogVO> calls(CallLogQuery query) {
        int days = Math.max(1, Math.min(query.getDays() == null ? 7 : query.getDays(), 30));
        LocalDateTime start = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        StringBuilder where = new StringBuilder(" WHERE created_at >= ?");
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(start));

        if (StringUtils.hasText(query.getRequestType())) {
            where.append(" AND request_type = ?");
            args.add(query.getRequestType().trim().toUpperCase(Locale.ROOT));
        }
        if (query.getSuccess() != null) {
            where.append(" AND success = ?");
            args.add(query.getSuccess() ? 1 : 0);
        }
        if (StringUtils.hasText(query.getDataSource())) {
            where.append(" AND data_source = ?");
            args.add(query.getDataSource().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            where.append(" AND (model_name LIKE ? OR request_summary LIKE ? OR trace_id LIKE ?)");
            String keyword = "%" + query.getKeyword().trim() + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_model_call_log" + where, Long.class, args.toArray());
        int size = (int) Math.max(1, Math.min(query.pageSize(), 100));
        int offset = (int) ((Math.max(1, query.currentPage()) - 1) * size);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(offset);
        List<CallLogVO> records = jdbcTemplate.query(
                "SELECT * FROM ai_model_call_log" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                this::mapCall, pageArgs.toArray());
        return PageResult.of(records, total == null ? 0 : total,
                Math.max(1, query.currentPage()), size);
    }

    private CallLogVO mapCall(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        BigDecimal cost = rs.getBigDecimal("estimated_cost");
        return new CallLogVO(
                rs.getLong("id"),
                rs.getString("trace_id"),
                rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                rs.getObject("model_config_id") == null ? null : rs.getLong("model_config_id"),
                rs.getString("provider_code"),
                rs.getString("model_name"),
                rs.getString("request_type"),
                rs.getObject("conversation_id") == null ? null : rs.getLong("conversation_id"),
                (Integer) rs.getObject("prompt_tokens"),
                (Integer) rs.getObject("completion_tokens"),
                (Integer) rs.getObject("total_tokens"),
                rs.getObject("latency_ms") == null ? null : rs.getLong("latency_ms"),
                rs.getBoolean("success"),
                rs.getString("data_source"),
                rs.getBoolean("estimated"),
                cost,
                cost != null,
                rs.getString("error_message"),
                rs.getString("request_summary"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
