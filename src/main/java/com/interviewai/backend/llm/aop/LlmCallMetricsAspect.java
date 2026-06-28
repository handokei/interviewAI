package com.interviewai.backend.llm.aop;

import com.interviewai.backend.interview.service.TokenEstimator;
import com.interviewai.backend.llm.annotation.LlmCalled;
import com.interviewai.backend.llm.model.LlmCallStatus;
import com.interviewai.backend.llm.service.LlmCallMetricService;
import com.interviewai.backend.llm.service.LlmCallMetricService.LlmCallSample;
import com.interviewai.backend.llm.service.SanitizeDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * {@link LlmCalled} annotation 메서드를 wrap하여 호출 결과를 metric으로 환산한다.
 *
 * <p>측정 항목:
 * <ul>
 *   <li>{@code durationMs} — start ~ finally 시점 차이</li>
 *   <li>{@code inputTokens} — String/Iterable 인자를 합쳐 {@link TokenEstimator}로 추정</li>
 *   <li>{@code outputTokens} — 반환값이 String/CharSequence면 그 길이 기반 추정</li>
 *   <li>{@code status / failureReason} — proceed 정상/예외 분기 + 예외 분류</li>
 *   <li>{@code sanitizeApplied} — 응답에 markdown/trailing comma 패턴 발견 여부</li>
 * </ul>
 *
 * <p>설계 원칙: <strong>metric AOP는 caller flow를 깨지 않는다.</strong> proceed가 던진
 * 예외는 metric 기록 후 그대로 다시 throw. metric 기록 실패는 warn 로그만.</p>
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LlmCallMetricsAspect {

    private final LlmCallMetricService metricService;
    private final SanitizeDetector sanitizeDetector;
    private final TokenEstimator tokenEstimator;

    @Around("@annotation(com.interviewai.backend.llm.annotation.LlmCalled)")
    public Object instrument(ProceedingJoinPoint joinPoint) throws Throwable {
        LlmCalled annotation = resolveAnnotation(joinPoint);
        String requestId = UUID.randomUUID().toString();
        int inputTokens = estimateInputTokens(joinPoint.getArgs());
        long start = System.nanoTime();

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            long durationMs = elapsedMs(start);
            recordSafe(new LlmCallSample(
                    requestId,
                    annotation.value(),
                    inputTokens,
                    0,
                    durationMs,
                    LlmCallStatus.FAILURE,
                    classifyFailure(ex),
                    false,
                    0
            ));
            throw ex;
        }

        long durationMs = elapsedMs(start);
        String responseText = textOfResult(result);
        int outputTokens = tokenEstimator.estimateTokens(responseText);
        boolean sanitize = sanitizeDetector.isSanitizeRequired(responseText);
        recordSafe(new LlmCallSample(
                requestId,
                annotation.value(),
                inputTokens,
                outputTokens,
                durationMs,
                LlmCallStatus.SUCCESS,
                null,
                sanitize,
                0
        ));
        return result;
    }

    private LlmCalled resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getAnnotation(LlmCalled.class);
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private int estimateInputTokens(Object[] args) {
        if (args == null) {
            return 0;
        }
        int total = 0;
        for (Object arg : args) {
            total += estimateOneArgTokens(arg);
        }
        return total;
    }

    private int estimateOneArgTokens(Object arg) {
        if (arg == null) {
            return 0;
        }
        if (arg instanceof CharSequence cs) {
            return tokenEstimator.estimateTokens(cs.toString());
        }
        if (arg instanceof Iterable<?> iter) {
            int subtotal = 0;
            for (Object item : iter) {
                subtotal += estimateOneArgTokens(item);
            }
            return subtotal;
        }
        // record(ChatMessage) 등은 toString이 무거우므로 보수적으로 0 처리
        return 0;
    }

    private String textOfResult(Object result) {
        if (result instanceof CharSequence cs) {
            return cs.toString();
        }
        return "";
    }

    /**
     * 예외를 운영 분류 5종(TIMEOUT / RATE_LIMIT / SERVER_ERROR / PARSE_ERROR / OTHER) 중 하나로 매핑.
     */
    String classifyFailure(Throwable ex) {
        String name = ex.getClass().getSimpleName().toLowerCase();
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (name.contains("timeout") || message.contains("timeout") || message.contains("timed out")) {
            return "TIMEOUT";
        }
        if (message.contains("429") || message.contains("rate limit") || message.contains("rate_limit")
                || message.contains("quota")) {
            return "RATE_LIMIT";
        }
        if (message.contains("500") || message.contains("502") || message.contains("503")
                || message.contains("server error")) {
            return "SERVER_ERROR";
        }
        if (name.contains("json") || name.contains("parse") || message.contains("parse")) {
            return "PARSE_ERROR";
        }
        return "OTHER";
    }

    private void recordSafe(LlmCallSample sample) {
        try {
            metricService.record(sample);
        } catch (RuntimeException ex) {
            log.warn("LLM metric 기록 실패 — {}", ex.getMessage());
        }
    }
}
