package com.interviewai.backend.interview.controller.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class InterviewStatsResponseDto {

    private long totalCount;
    private long completedCount;
    private long cancelledCount;
    private Map<String, Long> modeDistribution;
    private Map<String, Long> levelDistribution;
}
