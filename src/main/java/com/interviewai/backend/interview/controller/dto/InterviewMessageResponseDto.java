package com.interviewai.backend.interview.controller.dto;

import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InterviewMessageResponseDto {

    private Long id;
    private MessageRole role;
    private String content;
    private LocalDateTime createdAt;

    public static InterviewMessageResponseDto from(InterviewMessage message) {
        return InterviewMessageResponseDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
