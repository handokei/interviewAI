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
}
