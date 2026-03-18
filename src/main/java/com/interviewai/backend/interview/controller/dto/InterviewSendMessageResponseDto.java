package com.interviewai.backend.interview.controller.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterviewSendMessageResponseDto {

    private String aiResponse;
    private boolean isCompleted;
}
