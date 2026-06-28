package com.interviewai.backend.llm.service;

import com.interviewai.backend.llm.enums.PromptType;
import com.interviewai.backend.llm.model.LlmCallLog;
import com.interviewai.backend.llm.model.LlmCallStatus;
import com.interviewai.backend.llm.repository.LlmCallLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 호출 1건의 metric을 받아 (1) {@link LlmCallLog} 저장 (2) Micrometer counter/timer 발사.
 *
 * <p>저장은 {@code REQUIRES_NEW}로 분리해 호출자 트랜잭션 롤백이 metric 저장을 막지 않게 한다.
 * 즉 LLM 호출 자체는 성공했는데 caller의 후처리 transaction이 실패해도 metric은 남는다.</p>
 *
 * <p>예외 처리: metric 저장 자체가 실패해도 caller에 전파하지 않는다 (metric 책임이 도메인
 * flow를 깨면 안 됨). 실패는 warn 로그만.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCallMetricService {

    static final String COUNTER_CALLS = "llm.call.count";
    static final String COUNTER_INPUT_TOKENS = "llm.call.tokens.input";
    static final String COUNTER_OUTPUT_TOKENS = "llm.call.tokens.output";
    static final String COUNTER_SANITIZE = "llm.call.sanitize.applied";
    static final String COUNTER_RETRY = "llm.call.retry.count";

    private final LlmCallLogRepository repository;
    private final MeterRegistry meterRegistry;
    private final CostEstimator costEstimator;

    @Value("${spring.ai.openai.chat.options.model:gemini-2.5-flash}")
    private String model;

    /**
     * 호출 metric 1건 기록. 저장 실패 시 warn 로그만 남기고 caller에 던지지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LlmCallLog record(LlmCallSample sample) {
        Tags tags = Tags.of(
                "promptType", sample.promptType().name(),
                "status", sample.status().name(),
                "model", model
        );

        meterRegistry.counter(COUNTER_CALLS, tags).increment();
        meterRegistry.counter(COUNTER_INPUT_TOKENS, tags).increment(sample.inputTokens());
        meterRegistry.counter(COUNTER_OUTPUT_TOKENS, tags).increment(sample.outputTokens());
        if (sample.sanitizeApplied()) {
            meterRegistry.counter(COUNTER_SANITIZE, tags).increment();
        }
        if (sample.retryCount() > 0) {
            meterRegistry.counter(COUNTER_RETRY, tags).increment(sample.retryCount());
        }

        BigDecimal cost = costEstimator.estimate(model, sample.inputTokens(), sample.outputTokens());

        LlmCallLog logEntry = LlmCallLog.builder()
                .requestId(sample.requestId() != null ? sample.requestId() : UUID.randomUUID().toString())
                .model(model)
                .promptType(sample.promptType())
                .inputTokens(sample.inputTokens())
                .outputTokens(sample.outputTokens())
                .durationMs(sample.durationMs())
                .status(sample.status())
                .failureReason(sample.failureReason())
                .sanitizeApplied(sample.sanitizeApplied())
                .retryCount(sample.retryCount())
                .costEstimate(cost)
                .build();

        try {
            return repository.save(logEntry);
        } catch (RuntimeException ex) {
            log.warn("LLM metric 저장 실패 — promptType={}, status={}, err={}",
                    sample.promptType(), sample.status(), ex.getMessage());
            return logEntry;
        }
    }

    /** 호출 1건의 metric snapshot — 불변 record. */
    public record LlmCallSample(
            String requestId,
            PromptType promptType,
            int inputTokens,
            int outputTokens,
            long durationMs,
            LlmCallStatus status,
            String failureReason,
            boolean sanitizeApplied,
            int retryCount
    ) {
    }
}
