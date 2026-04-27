package com.interviewai.backend.interview.controller.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentTruncationInfo {

    private String fileName;
    private int originalLength;
    private int usedLength;
    private boolean truncated;
}
