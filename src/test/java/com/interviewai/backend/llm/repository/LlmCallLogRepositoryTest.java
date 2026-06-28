package com.interviewai.backend.llm.repository;

import com.interviewai.backend.llm.enums.PromptType;
import com.interviewai.backend.llm.model.LlmCallLog;
import com.interviewai.backend.llm.model.LlmCallStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@DirtiesContext
@DisplayName("LlmCallLogRepository 통합 테스트")
class LlmCallLogRepositoryTest {

    @Autowired
    private LlmCallLogRepository repository;

    private LocalDateTime windowStart;

    @BeforeEach
    void setUp() {
        windowStart = LocalDateTime.now().minusHours(24);
    }

    @Test
    @DisplayName("기능_테스트_save_후_id가_채워지고_findById로_조회가능하다")
    void 기능_테스트_save_후_id가_채워지고_findById로_조회가능하다() {
        LlmCallLog saved = repository.save(sample(PromptType.INTERVIEWER, LlmCallStatus.SUCCESS, 100, 50, false, 0));
        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("기능_테스트_countSince_는_윈도우_내_저장된_건수를_반환한다")
    void 기능_테스트_countSince_는_윈도우_내_저장된_건수를_반환한다() {
        repository.save(sample(PromptType.INTERVIEWER, LlmCallStatus.SUCCESS, 10, 5, false, 0));
        repository.save(sample(PromptType.EVALUATOR, LlmCallStatus.FAILURE, 20, 0, false, 1));
        assertThat(repository.countSince(windowStart)).isEqualTo(2);
    }

    @Test
    @DisplayName("기능_테스트_countByStatusSince_는_상태별로_필터링된다")
    void 기능_테스트_countByStatusSince_는_상태별로_필터링된다() {
        repository.save(sample(PromptType.INTERVIEWER, LlmCallStatus.SUCCESS, 10, 5, false, 0));
        repository.save(sample(PromptType.EVALUATOR, LlmCallStatus.SUCCESS, 20, 10, false, 0));
        repository.save(sample(PromptType.SUMMARIZER, LlmCallStatus.FAILURE, 30, 0, false, 2));

        assertThat(repository.countByStatusSince(windowStart, LlmCallStatus.SUCCESS)).isEqualTo(2);
        assertThat(repository.countByStatusSince(windowStart, LlmCallStatus.FAILURE)).isEqualTo(1);
    }

    @Test
    @DisplayName("기능_테스트_sumInputTokensSince_와_sumOutputTokensSince는_합계를_반환한다")
    void 기능_테스트_sumInputTokensSince_와_sumOutputTokensSince는_합계를_반환한다() {
        repository.save(sample(PromptType.INTERVIEWER, LlmCallStatus.SUCCESS, 100, 30, false, 0));
        repository.save(sample(PromptType.EVALUATOR, LlmCallStatus.SUCCESS, 200, 40, false, 0));

        assertThat(repository.sumInputTokensSince(windowStart)).isEqualTo(300);
        assertThat(repository.sumOutputTokensSince(windowStart)).isEqualTo(70);
    }

    @Test
    @DisplayName("기능_테스트_avgDurationMsSince_는_평균_duration을_반환한다")
    void 기능_테스트_avgDurationMsSince_는_평균_duration을_반환한다() {
        repository.save(sampleWithDuration(100));
        repository.save(sampleWithDuration(300));

        assertThat(repository.avgDurationMsSince(windowStart)).isEqualTo(200.0);
    }

    @Test
    @DisplayName("기능_테스트_countSanitizeAppliedSince_는_sanitize_적용된_건수만_세준다")
    void 기능_테스트_countSanitizeAppliedSince_는_sanitize_적용된_건수만_세준다() {
        repository.save(sample(PromptType.INTERVIEWER, LlmCallStatus.SUCCESS, 10, 5, true, 0));
        repository.save(sample(PromptType.INTERVIEWER, LlmCallStatus.SUCCESS, 10, 5, false, 0));
        repository.save(sample(PromptType.EVALUATOR, LlmCallStatus.SUCCESS, 10, 5, true, 0));

        assertThat(repository.countSanitizeAppliedSince(windowStart)).isEqualTo(2);
    }

    @Test
    @DisplayName("기능_테스트_sumRetryCountSince_는_retry_합계를_반환한다")
    void 기능_테스트_sumRetryCountSince_는_retry_합계를_반환한다() {
        repository.save(sample(PromptType.INTERVIEWER, LlmCallStatus.SUCCESS, 10, 5, false, 2));
        repository.save(sample(PromptType.EVALUATOR, LlmCallStatus.FAILURE, 20, 0, false, 3));

        assertThat(repository.sumRetryCountSince(windowStart)).isEqualTo(5);
    }

    @Test
    @DisplayName("기능_테스트_sumCostEstimateSince_는_비용_합계를_BigDecimal로_반환한다")
    void 기능_테스트_sumCostEstimateSince_는_비용_합계를_BigDecimal로_반환한다() {
        repository.save(sampleWithCost(new BigDecimal("0.00010000")));
        repository.save(sampleWithCost(new BigDecimal("0.00020000")));

        BigDecimal sum = repository.sumCostEstimateSince(windowStart);
        assertThat(sum).isEqualByComparingTo("0.00030000");
    }

    @Test
    @DisplayName("기능_테스트_저장된_건이_없으면_aggregate는_0이_반환된다")
    void 기능_테스트_저장된_건이_없으면_aggregate는_0이_반환된다() {
        assertThat(repository.countSince(windowStart)).isZero();
        assertThat(repository.sumInputTokensSince(windowStart)).isZero();
        assertThat(repository.avgDurationMsSince(windowStart)).isZero();
        assertThat(repository.sumCostEstimateSince(windowStart)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private LlmCallLog sample(PromptType type, LlmCallStatus status, int input, int output,
                              boolean sanitize, int retry) {
        return LlmCallLog.builder()
                .requestId("req-" + System.nanoTime())
                .model("gemini-2.5-flash")
                .promptType(type)
                .inputTokens(input)
                .outputTokens(output)
                .durationMs(500)
                .status(status)
                .failureReason(status == LlmCallStatus.FAILURE ? "OTHER" : null)
                .sanitizeApplied(sanitize)
                .retryCount(retry)
                .costEstimate(new BigDecimal("0.00001000"))
                .build();
    }

    private LlmCallLog sampleWithDuration(long durationMs) {
        return LlmCallLog.builder()
                .requestId("req-dur-" + System.nanoTime())
                .model("gemini-2.5-flash")
                .promptType(PromptType.INTERVIEWER)
                .inputTokens(10)
                .outputTokens(5)
                .durationMs(durationMs)
                .status(LlmCallStatus.SUCCESS)
                .sanitizeApplied(false)
                .retryCount(0)
                .costEstimate(BigDecimal.ZERO.setScale(8))
                .build();
    }

    private LlmCallLog sampleWithCost(BigDecimal cost) {
        return LlmCallLog.builder()
                .requestId("req-cost-" + System.nanoTime())
                .model("gemini-2.5-flash")
                .promptType(PromptType.INTERVIEWER)
                .inputTokens(10)
                .outputTokens(5)
                .durationMs(100)
                .status(LlmCallStatus.SUCCESS)
                .sanitizeApplied(false)
                .retryCount(0)
                .costEstimate(cost)
                .build();
    }
}
