package com.interviewai.backend.interview.controller.dto;

import com.interviewai.backend.interview.enums.AnswerLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterviewMessageEvalResponseDto {

    private boolean evaluated;
    private Boolean suggestFinish;
    private AnswerLevel answerLevel;
    private String qualityHint;
}
