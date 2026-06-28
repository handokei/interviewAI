package com.interviewai.backend.llm.enums;

/**
 * LLM 호출의 프롬프트 유형.
 *
 * <p>의사결정 #5(인터뷰어 응답 / 평가 / 요약 호출 분리)와 정합. 호출자 코드에 흩어진
 * 의도(intent)를 enum 한 곳으로 모아 metric/log/단가 산정의 group-by 키로 사용한다.</p>
 */
public enum PromptType {
    /** 면접관 답변 생성 — ChatModel.chat(...) */
    INTERVIEWER,

    /** 답변 평가 / suggestFinish JSON 생성 — ChatModel.chat(...) (evaluator prompt) */
    EVALUATOR,

    /** 대화 요약 — ChatModel.summarize(...) */
    SUMMARIZER
}
