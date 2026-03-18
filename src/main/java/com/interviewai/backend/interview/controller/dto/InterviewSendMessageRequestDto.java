package com.interviewai.backend.interview.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InterviewSendMessageRequestDto {

    @NotBlank(message = "메시지 내용은 필수입니다.")
    private String content;
}
