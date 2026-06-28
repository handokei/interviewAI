package com.interviewai.backend.interview.controller.dto;

import com.interviewai.backend.interview.enums.AnswerLevel;
import com.interviewai.backend.interview.model.InterviewFeedback;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InterviewFeedbackResponseDto {

    private Long id;
    private Long sessionId;
    private AnswerLevel overallLevel;
    private String strengths;
    private String improvements;
    private String fullReport;
    private LocalDateTime createdAt;

    public static InterviewFeedbackResponseDto from(InterviewFeedback feedback) {
        return InterviewFeedbackResponseDto.builder()
                .id(feedback.getId())
                .sessionId(feedback.getSession().getId())
                .overallLevel(feedback.getOverallLevel())
                .strengths(feedback.getStrengths())
                .improvements(feedback.getImprovements())
                .fullReport(feedback.getFullReport())
                .createdAt(feedback.getCreatedAt())
                .build();
    }
}
