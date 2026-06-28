package com.interviewai.backend.llm.service;

import com.interviewai.backend.llm.enums.PromptType;
import com.interviewai.backend.llm.model.LlmCallLog;
import com.interviewai.backend.llm.model.LlmCallStatus;
import com.interviewai.backend.llm.repository.LlmCallLogRepository;
import com.interviewai.backend.llm.service.LlmCallMetricService.LlmCallSample;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LlmCallMetricService 테스트")
@ExtendWith(MockitoExtension.class)
class LlmCallMetricServiceTest {

    @Mock
    private LlmCallLogRepository repository;

    private MeterRegistry meterRegistry;
    private CostEstimator costEstimator;
    private LlmCallMetricService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        costEstimator = new CostEstimator();
        service = new LlmCallMetricService(repository, meterRegistry, costEstimator);
        ReflectionTestUtils.setField(service, "model", "gemini-2.5-flash");
    }

    @Test
    @DisplayName("기능_테스트_record_호출_시_LlmCallLog가_repository에_저장된다")
    void 기능_테스트_record_호출_시_LlmCallLog가_repository에_저장된다() {
        LlmCallSample sample = new LlmCallSample(
                "req-1", PromptType.INTERVIEWER, 100, 50, 1234,
                LlmCallStatus.SUCCESS, null, false, 0
        );

        service.record(sample);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        LlmCallLog saved = captor.getValue();
        assertThat(saved.getRequestId()).isEqualTo("req-1");
        assertThat(saved.getPromptType()).isEqualTo(PromptType.INTERVIEWER);
        assertThat(saved.getInputTokens()).isEqualTo(100);
        assertThat(saved.getOutputTokens()).isEqualTo(50);
        assertThat(saved.getDurationMs()).isEqualTo(1234);
        assertThat(saved.getStatus()).isEqualTo(LlmCallStatus.SUCCESS);
        assertThat(saved.getModel()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    @DisplayName("기능_테스트_record_호출_시_Micrometer_call_count_counter가_증가한다")
    void 기능_테스트_record_호출_시_Micrometer_call_count_counter가_증가한다() {
        LlmCallSample sample = new LlmCallSample(
                "req-2", PromptType.EVALUATOR, 10, 5, 100,
                LlmCallStatus.SUCCESS, null, false, 0
        );

        service.record(sample);

        Counter counter = meterRegistry.find("llm.call.count")
                .tag("promptType", "EVALUATOR")
                .tag("status", "SUCCESS")
                .tag("model", "gemini-2.5-flash")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("기능_테스트_input_output_token_counter가_각각_누적된다")
    void 기능_테스트_input_output_token_counter가_각각_누적된다() {
        LlmCallSample sample = new LlmCallSample(
                "req-3", PromptType.SUMMARIZER, 200, 80, 500,
                LlmCallStatus.SUCCESS, null, false, 0
        );

        service.record(sample);

        Counter input = meterRegistry.find("llm.call.tokens.input")
                .tag("promptType", "SUMMARIZER").counter();
        Counter output = meterRegistry.find("llm.call.tokens.output")
                .tag("promptType", "SUMMARIZER").counter();
        assertThat(input).isNotNull();
        assertThat(output).isNotNull();
        assertThat(input.count()).isEqualTo(200.0);
        assertThat(output.count()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("기능_테스트_sanitizeApplied_가_true이면_sanitize_counter가_증가한다")
    void 기능_테스트_sanitizeApplied_가_true이면_sanitize_counter가_증가한다() {
        LlmCallSample sample = new LlmCallSample(
                "req-4", PromptType.EVALUATOR, 50, 20, 200,
                LlmCallStatus.SUCCESS, null, true, 0
        );

        service.record(sample);

        Counter sanitize = meterRegistry.find("llm.call.sanitize.applied")
                .tag("promptType", "EVALUATOR").counter();
        assertThat(sanitize).isNotNull();
        assertThat(sanitize.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("기능_테스트_sanitizeApplied_가_false이면_sanitize_counter는_등록되지_않는다")
    void 기능_테스트_sanitizeApplied_가_false이면_sanitize_counter는_등록되지_않는다() {
        LlmCallSample sample = new LlmCallSample(
                "req-5", PromptType.INTERVIEWER, 50, 20, 200,
                LlmCallStatus.SUCCESS, null, false, 0
        );

        service.record(sample);

        Counter sanitize = meterRegistry.find("llm.call.sanitize.applied")
                .tag("promptType", "INTERVIEWER").counter();
        assertThat(sanitize).isNull();
    }

    @Test
    @DisplayName("기능_테스트_retryCount_가_양수이면_retry_counter가_증가한다")
    void 기능_테스트_retryCount_가_양수이면_retry_counter가_증가한다() {
        LlmCallSample sample = new LlmCallSample(
                "req-6", PromptType.INTERVIEWER, 30, 10, 150,
                LlmCallStatus.SUCCESS, null, false, 3
        );

        service.record(sample);

        Counter retry = meterRegistry.find("llm.call.retry.count")
                .tag("promptType", "INTERVIEWER").counter();
        assertThat(retry).isNotNull();
        assertThat(retry.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("기능_테스트_retryCount_가_0이면_retry_counter는_등록되지_않는다")
    void 기능_테스트_retryCount_가_0이면_retry_counter는_등록되지_않는다() {
        LlmCallSample sample = new LlmCallSample(
                "req-7", PromptType.EVALUATOR, 30, 10, 150,
                LlmCallStatus.SUCCESS, null, false, 0
        );

        service.record(sample);

        Counter retry = meterRegistry.find("llm.call.retry.count")
                .tag("promptType", "EVALUATOR").counter();
        assertThat(retry).isNull();
    }

    @Test
    @DisplayName("기능_테스트_costEstimate_가_CostEstimator_의_계산값으로_채워진다")
    void 기능_테스트_costEstimate_가_CostEstimator_의_계산값으로_채워진다() {
        LlmCallSample sample = new LlmCallSample(
                "req-8", PromptType.INTERVIEWER, 1000, 500, 100,
                LlmCallStatus.SUCCESS, null, false, 0
        );

        service.record(sample);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        BigDecimal expected = costEstimator.estimate("gemini-2.5-flash", 1000, 500);
        assertThat(captor.getValue().getCostEstimate()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("기능_테스트_requestId_가_null이면_UUID로_자동_채워진다")
    void 기능_테스트_requestId_가_null이면_UUID로_자동_채워진다() {
        LlmCallSample sample = new LlmCallSample(
                null, PromptType.INTERVIEWER, 10, 5, 50,
                LlmCallStatus.SUCCESS, null, false, 0
        );

        service.record(sample);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        String requestId = captor.getValue().getRequestId();
        assertThat(requestId).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("기능_테스트_FAILURE_상태와_failureReason이_그대로_저장된다")
    void 기능_테스트_FAILURE_상태와_failureReason이_그대로_저장된다() {
        LlmCallSample sample = new LlmCallSample(
                "req-9", PromptType.EVALUATOR, 80, 0, 5000,
                LlmCallStatus.FAILURE, "TIMEOUT", false, 0
        );

        service.record(sample);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        LlmCallLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LlmCallStatus.FAILURE);
        assertThat(saved.getFailureReason()).isEqualTo("TIMEOUT");
        assertThat(saved.getOutputTokens()).isZero();
    }

    @Test
    @DisplayName("예외_테스트_repository_save_가_throw하면_warn_로그만_남기고_caller에_던지지_않는다")
    void 예외_테스트_repository_save_가_throw하면_warn_로그만_남기고_caller에_던지지_않는다() {
        when(repository.save(any(LlmCallLog.class))).thenThrow(new RuntimeException("DB down"));
        LlmCallSample sample = new LlmCallSample(
                "req-10", PromptType.INTERVIEWER, 1, 1, 1,
                LlmCallStatus.SUCCESS, null, false, 0
        );

        LlmCallLog result = service.record(sample);

        assertThat(result).isNotNull();
        assertThat(result.getRequestId()).isEqualTo("req-10");
        verify(repository, times(1)).save(any(LlmCallLog.class));
    }
}
