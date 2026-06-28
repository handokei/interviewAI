package com.interviewai.backend.llm.model;

import com.interviewai.backend.llm.enums.PromptType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmCallLog Entity 테스트")
class LlmCallLogTest {

    @Test
    @DisplayName("기능_테스트_builder로_생성하면_모든_필드가_정확히_설정된다")
    void 기능_테스트_builder로_생성하면_모든_필드가_정확히_설정된다() {
        LlmCallLog log = LlmCallLog.builder()
                .requestId("req-100")
                .model("gemini-2.5-flash")
                .promptType(PromptType.EVALUATOR)
                .inputTokens(120)
                .outputTokens(40)
                .durationMs(987)
                .status(LlmCallStatus.SUCCESS)
                .failureReason(null)
                .sanitizeApplied(true)
                .retryCount(2)
                .costEstimate(new BigDecimal("0.00012345"))
                .build();

        assertThat(log.getRequestId()).isEqualTo("req-100");
        assertThat(log.getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(log.getPromptType()).isEqualTo(PromptType.EVALUATOR);
        assertThat(log.getInputTokens()).isEqualTo(120);
        assertThat(log.getOutputTokens()).isEqualTo(40);
        assertThat(log.getDurationMs()).isEqualTo(987);
        assertThat(log.getStatus()).isEqualTo(LlmCallStatus.SUCCESS);
        assertThat(log.getFailureReason()).isNull();
        assertThat(log.isSanitizeApplied()).isTrue();
        assertThat(log.getRetryCount()).isEqualTo(2);
        assertThat(log.getCostEstimate()).isEqualByComparingTo("0.00012345");
    }

    @Test
    @DisplayName("기능_테스트_FAILURE_상태와_분류된_failureReason이_보존된다")
    void 기능_테스트_FAILURE_상태와_분류된_failureReason이_보존된다() {
        LlmCallLog log = LlmCallLog.builder()
                .requestId("req-fail")
                .model("gemini-2.5-flash")
                .promptType(PromptType.INTERVIEWER)
                .inputTokens(50)
                .outputTokens(0)
                .durationMs(5000)
                .status(LlmCallStatus.FAILURE)
                .failureReason("RATE_LIMIT")
                .sanitizeApplied(false)
                .retryCount(3)
                .costEstimate(BigDecimal.ZERO)
                .build();

        assertThat(log.getStatus()).isEqualTo(LlmCallStatus.FAILURE);
        assertThat(log.getFailureReason()).isEqualTo("RATE_LIMIT");
        assertThat(log.getOutputTokens()).isZero();
    }

    @Test
    @DisplayName("기능_테스트_LlmCallStatus는_SUCCESS와_FAILURE_두_상태로_정의된다")
    void 기능_테스트_LlmCallStatus는_SUCCESS와_FAILURE_두_상태로_정의된다() {
        assertThat(LlmCallStatus.values()).containsExactly(LlmCallStatus.SUCCESS, LlmCallStatus.FAILURE);
    }
}
