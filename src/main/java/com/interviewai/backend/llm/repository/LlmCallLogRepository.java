package com.interviewai.backend.llm.repository;

import com.interviewai.backend.llm.model.LlmCallLog;
import com.interviewai.backend.llm.model.LlmCallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@link LlmCallLog} 영속화 + actuator endpoint용 aggregate 조회.
 *
 * <p>모든 aggregate query는 {@code createdAt >= since} 범위로 받아 24h 윈도우 외에도
 * 후속 endpoint(1h / 7d 등)에서 재사용한다.</p>
 */
public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {

    @Query("SELECT COUNT(l) FROM LlmCallLog l WHERE l.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(l) FROM LlmCallLog l WHERE l.createdAt >= :since AND l.status = :status")
    long countByStatusSince(@Param("since") LocalDateTime since, @Param("status") LlmCallStatus status);

    @Query("SELECT COALESCE(SUM(l.inputTokens), 0) FROM LlmCallLog l WHERE l.createdAt >= :since")
    long sumInputTokensSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(l.outputTokens), 0) FROM LlmCallLog l WHERE l.createdAt >= :since")
    long sumOutputTokensSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(AVG(l.durationMs), 0) FROM LlmCallLog l WHERE l.createdAt >= :since")
    double avgDurationMsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(l) FROM LlmCallLog l WHERE l.createdAt >= :since AND l.sanitizeApplied = true")
    long countSanitizeAppliedSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(l.retryCount), 0) FROM LlmCallLog l WHERE l.createdAt >= :since")
    long sumRetryCountSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(l.costEstimate), 0) FROM LlmCallLog l WHERE l.createdAt >= :since")
    BigDecimal sumCostEstimateSince(@Param("since") LocalDateTime since);
}
