package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.enums.AnswerLevel;

public record InterviewEvaluation(boolean suggestFinish, AnswerLevel answerLevel, String qualityHint) {

    public static InterviewEvaluation fallback() {
        return new InterviewEvaluation(false, AnswerLevel.NEEDS_IMPROVEMENT, "답변이 접수되었습니다.");
    }
}
