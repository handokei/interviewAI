package com.interviewai.backend.interview.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class InterviewBatchDeleteRequestDto {

    @NotNull
    @NotEmpty(message = "삭제할 면접을 선택해 주세요.")
    @Size(max = 50, message = "한 번에 최대 50개까지 삭제할 수 있습니다.")
    private List<@NotNull Long> sessionIds;
}
