package com.interviewai.backend.llm.model;

/**
 * LLM 호출의 종료 상태. metric counter 라벨로 사용한다.
 */
public enum LlmCallStatus {
    /** 정상 완료. 응답 반환됨. */
    SUCCESS,

    /** 예외로 종료. {@code failureReason}에 분류된 사유가 기록된다. */
    FAILURE
}
