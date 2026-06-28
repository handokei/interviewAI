package com.interviewai.backend.llm.actuator;

import com.interviewai.backend.llm.actuator.LlmMetricsEndpoint.LlmMetricsSnapshot;
import com.interviewai.backend.llm.model.LlmCallStatus;
import com.interviewai.backend.llm.repository.LlmCallLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LlmMetricsEndpoint 테스트")
@ExtendWith(MockitoExtension.class)
class LlmMetricsEndpointTest {

    @Mock
    private LlmCallLogRepository repository;

    private LlmMetricsEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new LlmMetricsEndpoint(repository);
    }

    @Test
    @DisplayName("기능_테스트_24시간_aggregate_snapshot이_반환된다")
    void 기능_테스트_24시간_aggregate_snapshot이_반환된다() {
        when(repository.countSince(any())).thenReturn(100L);
        when(repository.countByStatusSince(any(), eq(LlmCallStatus.SUCCESS))).thenReturn(95L);
        when(repository.countByStatusSince(any(), eq(LlmCallStatus.FAILURE))).thenReturn(5L);
        when(repository.sumInputTokensSince(any())).thenReturn(50_000L);
        when(repository.sumOutputTokensSince(any())).thenReturn(20_000L);
        when(repository.avgDurationMsSince(any())).thenReturn(1234.5);
        when(repository.countSanitizeAppliedSince(any())).thenReturn(10L);
        when(repository.sumRetryCountSince(any())).thenReturn(7L);
        when(repository.sumCostEstimateSince(any())).thenReturn(new BigDecimal("0.12345678"));

        LlmMetricsSnapshot snapshot = endpoint.snapshot();

        assertThat(snapshot.windowHours()).isEqualTo(24);
        assertThat(snapshot.totalCalls()).isEqualTo(100);
        assertThat(snapshot.successCalls()).isEqualTo(95);
        assertThat(snapshot.failureCalls()).isEqualTo(5);
        assertThat(snapshot.failureRate()).isEqualTo(0.05);
        assertThat(snapshot.totalInputTokens()).isEqualTo(50_000);
        assertThat(snapshot.totalOutputTokens()).isEqualTo(20_000);
        assertThat(snapshot.avgDurationMs()).isEqualTo(1234.5);
        assertThat(snapshot.sanitizeAppliedCount()).isEqualTo(10);
        assertThat(snapshot.sanitizeAppliedRate()).isEqualTo(0.1);
        assertThat(snapshot.totalRetryCount()).isEqualTo(7);
        assertThat(snapshot.totalCostUsd()).isEqualByComparingTo(new BigDecimal("0.12345678"));
    }

    @Test
    @DisplayName("기능_테스트_호출이_0건이면_failureRate_와_sanitizeRate가_0이다_분모0_가드")
    void 기능_테스트_호출이_0건이면_failureRate_와_sanitizeRate가_0이다_분모0_가드() {
        when(repository.countSince(any())).thenReturn(0L);
        when(repository.countByStatusSince(any(), any())).thenReturn(0L);
        when(repository.sumInputTokensSince(any())).thenReturn(0L);
        when(repository.sumOutputTokensSince(any())).thenReturn(0L);
        when(repository.avgDurationMsSince(any())).thenReturn(0.0);
        when(repository.countSanitizeAppliedSince(any())).thenReturn(0L);
        when(repository.sumRetryCountSince(any())).thenReturn(0L);
        when(repository.sumCostEstimateSince(any())).thenReturn(BigDecimal.ZERO);

        LlmMetricsSnapshot snapshot = endpoint.snapshot();

        assertThat(snapshot.totalCalls()).isZero();
        assertThat(snapshot.failureRate()).isZero();
        assertThat(snapshot.sanitizeAppliedRate()).isZero();
    }

    @Test
    @DisplayName("예외_테스트_repository가_null_cost를_반환해도_0_USD로_안전하게_떨어진다")
    void 예외_테스트_repository가_null_cost를_반환해도_0_USD로_안전하게_떨어진다() {
        when(repository.countSince(any())).thenReturn(0L);
        when(repository.countByStatusSince(any(), any())).thenReturn(0L);
        when(repository.sumInputTokensSince(any())).thenReturn(0L);
        when(repository.sumOutputTokensSince(any())).thenReturn(0L);
        when(repository.avgDurationMsSince(any())).thenReturn(0.0);
        when(repository.countSanitizeAppliedSince(any())).thenReturn(0L);
        when(repository.sumRetryCountSince(any())).thenReturn(0L);
        when(repository.sumCostEstimateSince(any())).thenReturn(null);

        LlmMetricsSnapshot snapshot = endpoint.snapshot();

        assertThat(snapshot.totalCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("기능_테스트_since는_현재시각으로부터_24시간_전이다")
    void 기능_테스트_since는_현재시각으로부터_24시간_전이다() {
        when(repository.countSince(any())).thenReturn(0L);
        when(repository.countByStatusSince(any(), any())).thenReturn(0L);
        when(repository.sumInputTokensSince(any())).thenReturn(0L);
        when(repository.sumOutputTokensSince(any())).thenReturn(0L);
        when(repository.avgDurationMsSince(any())).thenReturn(0.0);
        when(repository.countSanitizeAppliedSince(any())).thenReturn(0L);
        when(repository.sumRetryCountSince(any())).thenReturn(0L);
        when(repository.sumCostEstimateSince(any())).thenReturn(BigDecimal.ZERO);

        LocalDateTime before = LocalDateTime.now().minusHours(24).minusSeconds(1);
        endpoint.snapshot();
        LocalDateTime after = LocalDateTime.now().minusHours(24).plusSeconds(1);

        org.mockito.ArgumentCaptor<LocalDateTime> captor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).countSince(captor.capture());
        LocalDateTime captured = captor.getValue();
        assertThat(captured).isAfter(before).isBefore(after);
    }
}
