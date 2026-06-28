package com.interviewai.backend.llm.actuator;

import com.interviewai.backend.llm.model.LlmCallStatus;
import com.interviewai.backend.llm.repository.LlmCallLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code /actuator/llm-metrics} — 최근 24시간 동안의 LLM 호출 운영 metric snapshot.
 *
 * <p>대시보드/사이드카 없이도 운영자가 단일 endpoint에서 token 합/실패율/평균 latency/
 * sanitize 빈도/retry/비용을 즉시 확인할 수 있다 (Prometheus와 별도, 사용자 read 전용).</p>
 */
@Component
@Endpoint(id = "llm-metrics")
@RequiredArgsConstructor
public class LlmMetricsEndpoint {

    static final int WINDOW_HOURS = 24;

    private final LlmCallLogRepository repository;

    @ReadOperation
    public LlmMetricsSnapshot snapshot() {
        LocalDateTime since = LocalDateTime.now().minusHours(WINDOW_HOURS);
        long total = repository.countSince(since);
        long success = repository.countByStatusSince(since, LlmCallStatus.SUCCESS);
        long failure = repository.countByStatusSince(since, LlmCallStatus.FAILURE);
        long inputTokens = repository.sumInputTokensSince(since);
        long outputTokens = repository.sumOutputTokensSince(since);
        double avgDuration = repository.avgDurationMsSince(since);
        long sanitizeApplied = repository.countSanitizeAppliedSince(since);
        long retryTotal = repository.sumRetryCountSince(since);
        BigDecimal totalCost = repository.sumCostEstimateSince(since);

        double failureRate = total == 0 ? 0.0 : (double) failure / total;
        double sanitizeRate = total == 0 ? 0.0 : (double) sanitizeApplied / total;

        return new LlmMetricsSnapshot(
                WINDOW_HOURS,
                total,
                success,
                failure,
                failureRate,
                inputTokens,
                outputTokens,
                avgDuration,
                sanitizeApplied,
                sanitizeRate,
                retryTotal,
                totalCost == null ? BigDecimal.ZERO : totalCost
        );
    }

    /**
     * 24h 윈도우 metric snapshot — actuator JSON 응답.
     */
    public record LlmMetricsSnapshot(
            int windowHours,
            long totalCalls,
            long successCalls,
            long failureCalls,
            double failureRate,
            long totalInputTokens,
            long totalOutputTokens,
            double avgDurationMs,
            long sanitizeAppliedCount,
            double sanitizeAppliedRate,
            long totalRetryCount,
            BigDecimal totalCostUsd
    ) {
    }
}
