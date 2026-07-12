package com.interviewai.backend.interview.service;

import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.global.config.InterviewProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseEmitterHelperTest {

    private SseEmitterHelper sseEmitterHelper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        InterviewProperties properties = new InterviewProperties();
        properties.getSse().setTimeoutMs(120_000L);

        sseEmitterHelper = new SseEmitterHelper(properties, meterRegistry);
        sseEmitterHelper.initMetrics();
    }

    @Test
    @DisplayName("기능_테스트_createEmitter_SseEmitter를_반환한다")
    void 기능_테스트_createEmitter_SseEmitter를_반환한다() {
        SseEmitter emitter = sseEmitterHelper.createEmitter();
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("기능_테스트_createEmitter_activeConnections_게이지가_증가한다")
    void 기능_테스트_createEmitter_activeConnections_게이지가_증가한다() {
        sseEmitterHelper.createEmitter();
        sseEmitterHelper.createEmitter();

        assertThat(sseEmitterHelper.getActiveConnectionCount()).isEqualTo(2);

        Gauge gauge = meterRegistry.find("sse_active_connections").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("기능_테스트_handleCompletion_activeConnections_게이지가_감소한다")
    void 기능_테스트_handleCompletion_activeConnections_게이지가_감소한다() {
        sseEmitterHelper.createEmitter();
        assertThat(sseEmitterHelper.getActiveConnectionCount()).isEqualTo(1);

        sseEmitterHelper.handleCompletion();
        assertThat(sseEmitterHelper.getActiveConnectionCount()).isEqualTo(0);

        Gauge gauge = meterRegistry.find("sse_active_connections").gauge();
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("기능_테스트_handleCompletion_complete_카운터가_증가한다")
    void 기능_테스트_handleCompletion_complete_카운터가_증가한다() {
        sseEmitterHelper.createEmitter();
        sseEmitterHelper.handleCompletion();

        Counter completeCounter = meterRegistry.find("sse_disconnections_total")
                .tag("reason", "complete").counter();
        assertThat(completeCounter).isNotNull();
        assertThat(completeCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("기능_테스트_handleError_error_카운터가_증가한다")
    void 기능_테스트_handleError_error_카운터가_증가한다() {
        sseEmitterHelper.createEmitter();
        sseEmitterHelper.handleError(new RuntimeException("테스트 에러"));

        Counter errorCounter = meterRegistry.find("sse_disconnections_total")
                .tag("reason", "error").counter();
        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("기능_테스트_handleTimeout_timeout_카운터가_증가한다")
    void 기능_테스트_handleTimeout_timeout_카운터가_증가한다() {
        sseEmitterHelper.createEmitter();
        sseEmitterHelper.handleTimeout();

        Counter timeoutCounter = meterRegistry.find("sse_disconnections_total")
                .tag("reason", "timeout").counter();
        assertThat(timeoutCounter).isNotNull();
        assertThat(timeoutCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("기능_테스트_sendToken_정상적으로_토큰을_전송한다")
    void 기능_테스트_sendToken_정상적으로_토큰을_전송한다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        sseEmitterHelper.sendToken(emitter, "안녕하세요");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("예외_테스트_sendToken_IOException_발생_시_RuntimeException으로_래핑된다")
    void 예외_테스트_sendToken_IOException_발생_시_RuntimeException으로_래핑된다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(IOException.class).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        assertThatThrownBy(() -> sseEmitterHelper.sendToken(emitter, "토큰"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("기능_테스트_completeWithServiceBusyMessage_안내_메시지_전송_후_정상_종료한다")
    void 기능_테스트_completeWithServiceBusyMessage_안내_메시지_전송_후_정상_종료한다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);

        sseEmitterHelper.completeWithServiceBusyMessage(emitter, new RuntimeException("429"));

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    @DisplayName("예외_테스트_completeWithServiceBusyMessage_안내_전송_실패_시_오류로_종료한다")
    void 예외_테스트_completeWithServiceBusyMessage_안내_전송_실패_시_오류로_종료한다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        RuntimeException cause = new RuntimeException("429");
        doThrow(IOException.class).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        sseEmitterHelper.completeWithServiceBusyMessage(emitter, cause);

        verify(emitter).completeWithError(cause);
    }

    @Test
    @DisplayName("기능_테스트_SERVICE_BUSY_MESSAGE_상수가_사용자_안내_문구를_담고_있다")
    void 기능_테스트_SERVICE_BUSY_MESSAGE_상수가_사용자_안내_문구를_담고_있다() {
        assertThat(SseEmitterHelper.SERVICE_BUSY_MESSAGE)
                .isEqualTo("일시적으로 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    @Test
    @DisplayName("기능_테스트_여러_emitter_콜백_시_메트릭이_정확하다")
    void 기능_테스트_여러_emitter_콜백_시_메트릭이_정확하다() {
        sseEmitterHelper.createEmitter();
        sseEmitterHelper.createEmitter();
        sseEmitterHelper.createEmitter();

        assertThat(sseEmitterHelper.getActiveConnectionCount()).isEqualTo(3);

        sseEmitterHelper.handleCompletion();
        assertThat(sseEmitterHelper.getActiveConnectionCount()).isEqualTo(2);

        sseEmitterHelper.handleError(new RuntimeException("에러"));
        sseEmitterHelper.handleCompletion();
        assertThat(sseEmitterHelper.getActiveConnectionCount()).isEqualTo(1);

        sseEmitterHelper.handleCompletion();
        assertThat(sseEmitterHelper.getActiveConnectionCount()).isEqualTo(0);

        Counter completeCounter = meterRegistry.find("sse_disconnections_total")
                .tag("reason", "complete").counter();
        Counter errorCounter = meterRegistry.find("sse_disconnections_total")
                .tag("reason", "error").counter();
        assertThat(completeCounter.count()).isEqualTo(3.0);
        assertThat(errorCounter.count()).isEqualTo(1.0);
    }
}
