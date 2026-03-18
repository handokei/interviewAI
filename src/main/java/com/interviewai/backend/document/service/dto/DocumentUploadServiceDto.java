package com.interviewai.backend.document.service.dto;

import com.interviewai.backend.document.enums.DocumentType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentUploadServiceDto {

    private Long userId;
    private DocumentType documentType;
    private String originalFileName;
    private String s3Key;
    private String parsedText;
}
