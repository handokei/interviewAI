package com.interviewai.backend.interview.controller.dto;

import com.interviewai.backend.interview.enums.AnswerLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterviewSendMessageResponseDto {

    private String aiResponse;
    private boolean isCompleted;
    private boolean suggestFinish;
    private AnswerLevel answerLevel;
    private String qualityHint;
}
