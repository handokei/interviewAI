package com.interviewai.backend.interview.service;

public record InterviewEvaluation(boolean suggestFinish, int qualityScore, String qualityHint) {

    public static InterviewEvaluation fallback() {
        return new InterviewEvaluation(false, 50, "답변이 접수되었습니다.");
    }
}
