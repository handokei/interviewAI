package com.interviewai.backend.global.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 외부 클라이언트 호출의 실행 시간을 측정하는 AOP Aspect.
 * 대상: com.interviewai.backend.client 패키지 하위 모든 메서드
 * (JobCrawlerClient, GithubApiClient, ClaudeAiClient, PdfParserClient)
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PerformanceLoggingAspect {

    private final MeterRegistry meterRegistry;

    @Around("execution(* com.interviewai.backend.client..*(..))")
    public Object logClientCalls(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long nanos = System.nanoTime() - start;
            long ms = nanos / 1_000_000;
            log.info("[PERF] {}.{} — {}ms", className, methodName, ms);
            Timer.builder("client.call.duration")
                    .tag("class", className)
                    .tag("method", methodName)
                    .register(meterRegistry)
                    .record(nanos, TimeUnit.NANOSECONDS);
        }
    }
}
