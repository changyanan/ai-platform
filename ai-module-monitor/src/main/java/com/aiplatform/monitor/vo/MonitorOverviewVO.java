package com.aiplatform.monitor.vo;

import java.math.BigDecimal;
import java.util.List;

public record MonitorOverviewVO(
        Summary summary,
        List<TrendPoint> trend,
        List<TypeStat> types,
        List<ModelStat> models,
        List<CallLogVO> recentCalls
) {

    public record Summary(
            long totalCalls,
            long realCalls,
            long simulatedCalls,
            long successCalls,
            long failedCalls,
            double successRate,
            Long avgLatencyMs,
            long totalTokens,
            BigDecimal knownCost,
            long unknownCostCalls,
            long agentExecutions,
            long toolCalls,
            long ragQueries,
            long multimodalTasks,
            long activeModels,
            long enabledAgents,
            long enabledTools,
            long knowledgeBases,
            long knowledgeDocuments,
            long knowledgeChunks
    ) {
    }

    public record TrendPoint(
            String date,
            long calls,
            long successCalls,
            long totalTokens,
            Long avgLatencyMs
    ) {
    }

    public record TypeStat(
            String name,
            long calls,
            long successCalls
    ) {
    }

    public record ModelStat(
            String name,
            long calls,
            long totalTokens,
            Long avgLatencyMs,
            double successRate
    ) {
    }
}
