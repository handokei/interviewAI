package com.interviewai.backend.interview.controller.dto;

import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class InterviewStartRequestDto {

    @NotNull(message = "면접 모드는 필수입니다.")
    private InterviewMode mode;

    @NotNull(message = "면접 레벨은 필수입니다.")
    private InterviewLevel level;

    private String jobTitle;

    private List<Long> documentIds;

    private String jobPostingUrl;

    private String githubUrl;
}
