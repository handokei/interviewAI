package com.interviewai.backend.interview.controller.dto;

import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Deprecated
    private String githubUrl;

    @Size(max = 3, message = "GitHub 레포 URL은 최대 3개까지 입력 가능합니다.")
    private List<String> githubRepoUrls;

    public List<String> getEffectiveGithubRepoUrls() {
        if (githubRepoUrls != null && !githubRepoUrls.isEmpty()) {
            return githubRepoUrls;
        }
        if (githubUrl != null && !githubUrl.isEmpty()) {
            return List.of(githubUrl);
        }
        return List.of();
    }
}
