package com.interviewai.backend.llm.annotation;

import com.interviewai.backend.llm.enums.PromptType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * LLM 호출 metric 수집 대상 메서드를 표시한다.
 *
 * <p>AOP({@code LlmCallMetricsAspect})가 본 annotation이 붙은 메서드를 wrap하여
 * latency / 실패율 / sanitize 발동 / token 추정 / 비용 추정을 자동 기록한다.</p>
 *
 * <p>관심사 분리: 호출자(서비스)는 LLM 호출만 한다. metric 책임은 annotation + AOP가 가진다.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LlmCalled {

    /**
     * 프롬프트 유형 — INTERVIEWER / EVALUATOR / SUMMARIZER. metric tag 및 단가 산정 키.
     */
    PromptType value();
}
