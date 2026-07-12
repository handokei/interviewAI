package com.interviewai.backend.interview.service;

import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.interview.enums.InterviewErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class SseEmitterHelper {

    // LLM 호출이 재시도까지 소진하고도 실패했을 때(429 RPM/RPD 초과 등) 프론트에 보여줄 안내 메시지.
    // 스트림을 조용히 종료(빈 화면 stall)하지 않고 이 메시지를 먼저 내려준다.
    public static final String SERVICE_BUSY_MESSAGE = "일시적으로 혼잡합니다. 잠시 후 다시 시도해주세요.";

    private final long timeoutMs;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    private Counter timeoutCounter;
    private Counter errorCounter;
    private Counter completeCounter;

    public SseEmitterHelper(
            com.interviewai.backend.global.config.InterviewProperties interviewProperties,
            MeterRegistry meterRegistry) {
        this.timeoutMs = interviewProperties.getSse().getTimeoutMs();
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void initMetrics() {
        meterRegistry.gauge("sse_active_connections", activeConnections);
        timeoutCounter = Counter.builder("sse_disconnections_total")
                .tag("reason", "timeout")
                .register(meterRegistry);
        errorCounter = Counter.builder("sse_disconnections_total")
                .tag("reason", "error")
                .register(meterRegistry);
        completeCounter = Counter.builder("sse_disconnections_total")
                .tag("reason", "complete")
                .register(meterRegistry);
    }

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        activeConnections.incrementAndGet();

        emitter.onTimeout(() -> handleTimeout());
        emitter.onError(throwable -> handleError(throwable));
        emitter.onCompletion(() -> handleCompletion());

        return emitter;
    }

    void handleTimeout() {
        log.warn("SSE 연결 타임아웃 ({}ms)", timeoutMs);
        timeoutCounter.increment();
    }

    void handleError(Throwable throwable) {
        log.error("SSE 연결 에러", throwable);
        errorCounter.increment();
    }

    void handleCompletion() {
        activeConnections.decrementAndGet();
        completeCounter.increment();
    }

    int getActiveConnectionCount() {
        return activeConnections.get();
    }

    public void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().data(token));
        } catch (IOException e) {
            throw new BusinessException(InterviewErrorCode.SSE_SEND_FAILED);
        }
    }

    /**
     * LLM 호출 실패(재시도 소진 등) 시 graceful degradation.
     * 스트림을 조용히 끊지 않고 사용자용 안내 메시지를 'error' 이벤트로 내려준 뒤 emitter를 정상 종료한다.
     * 안내 전송 자체가 실패하면(클라이언트 이미 이탈 등) 오류로 종료한다.
     */
    public void completeWithServiceBusyMessage(SseEmitter emitter, Throwable cause) {
        log.warn("LLM 스트리밍 실패, graceful degradation 안내 전송", cause);
        try {
            emitter.send(SseEmitter.event().name("error").data(SERVICE_BUSY_MESSAGE));
            emitter.complete();
        } catch (IOException | RuntimeException e) {
            log.error("graceful degradation 안내 전송 실패, emitter 오류 종료", e);
            emitter.completeWithError(cause);
        }
    }
}
