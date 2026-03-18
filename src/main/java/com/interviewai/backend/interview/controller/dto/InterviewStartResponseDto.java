package com.interviewai.backend.interview.controller.dto;

import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import com.interviewai.backend.interview.enums.InterviewStatus;
import com.interviewai.backend.interview.model.InterviewSession;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InterviewStartResponseDto {

    private Long sessionId;
    private InterviewMode mode;
    private InterviewLevel level;
    private InterviewStatus status;
    private String firstQuestion;
    private LocalDateTime createdAt;

    public static InterviewStartResponseDto of(InterviewSession session, String firstQuestion) {
        return InterviewStartResponseDto.builder()
                .sessionId(session.getId())
                .mode(session.getMode())
                .level(session.getLevel())
                .status(session.getStatus())
                .firstQuestion(firstQuestion)
                .createdAt(session.getCreatedAt())
                .build();
    }
}
