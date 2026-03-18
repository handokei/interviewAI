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
public class InterviewSessionResponseDto {

    private Long id;
    private InterviewMode mode;
    private InterviewLevel level;
    private InterviewStatus status;
    private String jobTitle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InterviewSessionResponseDto from(InterviewSession session) {
        return InterviewSessionResponseDto.builder()
                .id(session.getId())
                .mode(session.getMode())
                .level(session.getLevel())
                .status(session.getStatus())
                .jobTitle(session.getJobTitle())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
