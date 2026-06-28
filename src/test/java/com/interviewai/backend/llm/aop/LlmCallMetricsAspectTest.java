package com.interviewai.backend.llm.aop;

import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.interview.service.TokenEstimator;
import com.interviewai.backend.llm.annotation.LlmCalled;
import com.interviewai.backend.llm.enums.PromptType;
import com.interviewai.backend.llm.model.LlmCallStatus;
import com.interviewai.backend.llm.service.LlmCallMetricService;
import com.interviewai.backend.llm.service.LlmCallMetricService.LlmCallSample;
import com.interviewai.backend.llm.service.SanitizeDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LlmCallMetricsAspect 테스트")
@ExtendWith(MockitoExtension.class)
class LlmCallMetricsAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private LlmCallMetricService metricService;

    private SanitizeDetector sanitizeDetector;
    private TokenEstimator tokenEstimator;
    private LlmCallMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        sanitizeDetector = new SanitizeDetector();
        InterviewProperties props = new InterviewProperties();
        props.getPrompt().setCharsPerToken(3);
        tokenEstimator = new TokenEstimator(props, new SimpleMeterRegistry());
        aspect = new LlmCallMetricsAspect(metricService, sanitizeDetector, tokenEstimator);
    }

    @Test
    @DisplayName("기능_테스트_정상_호출_시_SUCCESS_상태로_metric이_기록된다")
    void 기능_테스트_정상_호출_시_SUCCESS_상태로_metric이_기록된다() throws Throwable {
        stubAnnotatedMethod("interviewerMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"prompt content"});
        when(joinPoint.proceed()).thenReturn("clean response");

        Object result = aspect.instrument(joinPoint);

        assertThat(result).isEqualTo("clean response");
        ArgumentCaptor<LlmCallSample> captor = ArgumentCaptor.forClass(LlmCallSample.class);
        verify(metricService).record(captor.capture());
        LlmCallSample sample = captor.getValue();
        assertThat(sample.status()).isEqualTo(LlmCallStatus.SUCCESS);
        assertThat(sample.promptType()).isEqualTo(PromptType.INTERVIEWER);
        assertThat(sample.failureReason()).isNull();
        assertThat(sample.sanitizeApplied()).isFalse();
    }

    @Test
    @DisplayName("기능_테스트_응답에_markdown_fence가_있으면_sanitizeApplied_true로_기록된다")
    void 기능_테스트_응답에_markdown_fence가_있으면_sanitizeApplied_true로_기록된다() throws Throwable {
        stubAnnotatedMethod("evaluatorMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"q"});
        when(joinPoint.proceed()).thenReturn("```json\n{}\n```");

        aspect.instrument(joinPoint);

        ArgumentCaptor<LlmCallSample> captor = ArgumentCaptor.forClass(LlmCallSample.class);
        verify(metricService).record(captor.capture());
        assertThat(captor.getValue().sanitizeApplied()).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_String_과_List_인자의_token이_누적되어_input으로_기록된다")
    void 기능_테스트_String_과_List_인자의_token이_누적되어_input으로_기록된다() throws Throwable {
        stubAnnotatedMethod("interviewerMethod");
        // "abcdefghi" (9 chars / 3) + ["123", "456"] each 3 chars / 3 = 1+1+1 = 3 → total 3+3=6
        when(joinPoint.getArgs()).thenReturn(new Object[]{"abcdefghi", List.of("123", "456")});
        when(joinPoint.proceed()).thenReturn("xy");

        aspect.instrument(joinPoint);

        ArgumentCaptor<LlmCallSample> captor = ArgumentCaptor.forClass(LlmCallSample.class);
        verify(metricService).record(captor.capture());
        assertThat(captor.getValue().inputTokens()).isGreaterThan(0);
    }

    @Test
    @DisplayName("기능_테스트_String이_아닌_인자는_token_추정에서_0으로_처리된다")
    void 기능_테스트_String이_아닌_인자는_token_추정에서_0으로_처리된다() throws Throwable {
        stubAnnotatedMethod("interviewerMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{42, new Object()});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.instrument(joinPoint);

        ArgumentCaptor<LlmCallSample> captor = ArgumentCaptor.forClass(LlmCallSample.class);
        verify(metricService).record(captor.capture());
        assertThat(captor.getValue().inputTokens()).isZero();
    }

    @Test
    @DisplayName("기능_테스트_args가_null이면_inputTokens_0으로_기록된다")
    void 기능_테스트_args가_null이면_inputTokens_0으로_기록된다() throws Throwable {
        stubAnnotatedMethod("interviewerMethod");
        when(joinPoint.getArgs()).thenReturn(null);
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.instrument(joinPoint);

        ArgumentCaptor<LlmCallSample> captor = ArgumentCaptor.forClass(LlmCallSample.class);
        verify(metricService).record(captor.capture());
        assertThat(captor.getValue().inputTokens()).isZero();
    }

    @Test
    @DisplayName("예외_테스트_proceed가_throw하면_FAILURE_상태로_metric기록_후_같은_예외가_재던져진다")
    void 예외_테스트_proceed가_throw하면_FAILURE_상태로_metric기록_후_같은_예외가_재던져진다() throws Throwable {
        stubAnnotatedMethod("interviewerMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});
        RuntimeException expected = new RuntimeException("boom");
        when(joinPoint.proceed()).thenThrow(expected);

        assertThatThrownBy(() -> aspect.instrument(joinPoint)).isSameAs(expected);

        ArgumentCaptor<LlmCallSample> captor = ArgumentCaptor.forClass(LlmCallSample.class);
        verify(metricService).record(captor.capture());
        LlmCallSample sample = captor.getValue();
        assertThat(sample.status()).isEqualTo(LlmCallStatus.FAILURE);
        assertThat(sample.outputTokens()).isZero();
        assertThat(sample.failureReason()).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("기능_테스트_metricService가_throw해도_caller는_정상_반환값을_받는다")
    void 기능_테스트_metricService가_throw해도_caller는_정상_반환값을_받는다() throws Throwable {
        stubAnnotatedMethod("interviewerMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});
        when(joinPoint.proceed()).thenReturn("ok");
        org.mockito.Mockito.doThrow(new RuntimeException("metric DB down"))
                .when(metricService).record(any());

        Object result = aspect.instrument(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(metricService, times(1)).record(any());
    }

    @Test
    @DisplayName("기능_테스트_TimeoutException은_TIMEOUT으로_분류된다")
    void 기능_테스트_TimeoutException은_TIMEOUT으로_분류된다() {
        String reason = aspect.classifyFailure(new SocketTimeoutException("read timed out"));
        assertThat(reason).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("기능_테스트_429_또는_rate_limit_메시지는_RATE_LIMIT으로_분류된다")
    void 기능_테스트_429_또는_rate_limit_메시지는_RATE_LIMIT으로_분류된다() {
        assertThat(aspect.classifyFailure(new RuntimeException("HTTP 429 Too Many Requests")))
                .isEqualTo("RATE_LIMIT");
        assertThat(aspect.classifyFailure(new RuntimeException("rate limit exceeded")))
                .isEqualTo("RATE_LIMIT");
        assertThat(aspect.classifyFailure(new RuntimeException("quota exceeded")))
                .isEqualTo("RATE_LIMIT");
    }

    @Test
    @DisplayName("기능_테스트_5xx_메시지는_SERVER_ERROR로_분류된다")
    void 기능_테스트_5xx_메시지는_SERVER_ERROR로_분류된다() {
        assertThat(aspect.classifyFailure(new RuntimeException("500 internal")))
                .isEqualTo("SERVER_ERROR");
        assertThat(aspect.classifyFailure(new RuntimeException("503 service unavailable")))
                .isEqualTo("SERVER_ERROR");
        assertThat(aspect.classifyFailure(new RuntimeException("upstream server error")))
                .isEqualTo("SERVER_ERROR");
    }

    @Test
    @DisplayName("기능_테스트_Json_또는_Parse_예외는_PARSE_ERROR로_분류된다")
    void 기능_테스트_Json_또는_Parse_예외는_PARSE_ERROR로_분류된다() {
        class JsonProcessingException extends RuntimeException {
            JsonProcessingException(String msg) { super(msg); }
        }
        assertThat(aspect.classifyFailure(new JsonProcessingException("bad json")))
                .isEqualTo("PARSE_ERROR");
        assertThat(aspect.classifyFailure(new RuntimeException("could not parse response")))
                .isEqualTo("PARSE_ERROR");
    }

    @Test
    @DisplayName("기능_테스트_분류되지_않는_예외는_OTHER로_분류된다")
    void 기능_테스트_분류되지_않는_예외는_OTHER로_분류된다() {
        assertThat(aspect.classifyFailure(new RuntimeException("unknown failure")))
                .isEqualTo("OTHER");
        assertThat(aspect.classifyFailure(new IllegalStateException()))
                .isEqualTo("OTHER");
    }

    @Test
    @DisplayName("기능_테스트_502_와_timed_out_메시지도_각_분류로_매핑된다")
    void 기능_테스트_502_와_timed_out_메시지도_각_분류로_매핑된다() {
        assertThat(aspect.classifyFailure(new RuntimeException("502 bad gateway")))
                .isEqualTo("SERVER_ERROR");
        assertThat(aspect.classifyFailure(new RuntimeException("connection timed out")))
                .isEqualTo("TIMEOUT");
        assertThat(aspect.classifyFailure(new RuntimeException("rate_limit triggered")))
                .isEqualTo("RATE_LIMIT");
    }

    @Test
    @DisplayName("기능_테스트_args_iterable_내부에_null_요소가_있어도_안전하게_0으로_처리된다")
    void 기능_테스트_args_iterable_내부에_null_요소가_있어도_안전하게_0으로_처리된다() throws Throwable {
        stubAnnotatedMethod("interviewerMethod");
        java.util.List<Object> argsList = new java.util.ArrayList<>();
        argsList.add(null);
        argsList.add("abc");
        when(joinPoint.getArgs()).thenReturn(new Object[]{argsList});
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.instrument(joinPoint);

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<LlmCallSample> captor = ArgumentCaptor.forClass(LlmCallSample.class);
        verify(metricService).record(captor.capture());
        assertThat(captor.getValue().inputTokens()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("기능_테스트_proceed_결과가_CharSequence가_아니면_outputTokens_0으로_기록된다")
    void 기능_테스트_proceed_결과가_CharSequence가_아니면_outputTokens_0으로_기록된다() throws Throwable {
        stubAnnotatedMethod("interviewerMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});
        when(joinPoint.proceed()).thenReturn(42);

        Object result = aspect.instrument(joinPoint);

        assertThat(result).isEqualTo(42);
        ArgumentCaptor<LlmCallSample> captor = ArgumentCaptor.forClass(LlmCallSample.class);
        verify(metricService).record(captor.capture());
        assertThat(captor.getValue().outputTokens()).isZero();
    }

    @Test
    @DisplayName("기능_테스트_메시지가_null인_예외는_class_이름_기반으로_분류된다")
    void 기능_테스트_메시지가_null인_예외는_class_이름_기반으로_분류된다() {
        assertThat(aspect.classifyFailure(new java.util.concurrent.TimeoutException()))
                .isEqualTo("TIMEOUT");
        assertThat(aspect.classifyFailure(new RuntimeException()))
                .isEqualTo("OTHER");
    }

    @Test
    @DisplayName("기능_테스트_class_이름에_parse_가_포함되면_PARSE_ERROR로_분류된다")
    void 기능_테스트_class_이름에_parse_가_포함되면_PARSE_ERROR로_분류된다() {
        class CustomParseException extends RuntimeException {
            CustomParseException() { super(); }
        }
        assertThat(aspect.classifyFailure(new CustomParseException()))
                .isEqualTo("PARSE_ERROR");
    }

    @Test
    @DisplayName("기능_테스트_빈_메시지_예외도_분류_체인을_모두_통과해_OTHER로_떨어진다")
    void 기능_테스트_빈_메시지_예외도_분류_체인을_모두_통과해_OTHER로_떨어진다() {
        assertThat(aspect.classifyFailure(new RuntimeException("")))
                .isEqualTo("OTHER");
    }

    @Test
    @DisplayName("기능_테스트_class_이름은_무관하지만_메시지에_timeout이_있으면_TIMEOUT으로_분류된다")
    void 기능_테스트_class_이름은_무관하지만_메시지에_timeout이_있으면_TIMEOUT으로_분류된다() {
        assertThat(aspect.classifyFailure(new RuntimeException("upstream timeout received")))
                .isEqualTo("TIMEOUT");
    }

    private void stubAnnotatedMethod(String methodName) throws NoSuchMethodException {
        Method method = AnnotatedSamples.class.getDeclaredMethod(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
    }

    /** AOP가 메서드 단위 annotation을 읽도록 reflection target을 제공한다. */
    @SuppressWarnings("unused")
    static class AnnotatedSamples {
        @LlmCalled(PromptType.INTERVIEWER)
        public String interviewerMethod() { return ""; }

        @LlmCalled(PromptType.EVALUATOR)
        public String evaluatorMethod() { return ""; }

        @LlmCalled(PromptType.SUMMARIZER)
        public String summarizerMethod() { return ""; }
    }
}
