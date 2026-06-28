package com.interviewai.backend.document.controller.dto;

import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.document.model.UserDocument;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentUploadResponseDto {

    private Long id;
    private DocumentType documentType;
    private String originalFileName;
    private int parsedTextLength;
    private int maxUsableLength;
    private boolean willBeTruncated;
    private LocalDateTime createdAt;

    public static DocumentUploadResponseDto from(UserDocument document, int maxDocumentLength) {
        int textLength = document.getParsedText() != null ? document.getParsedText().length() : 0;
        return DocumentUploadResponseDto.builder()
                .id(document.getId())
                .documentType(document.getDocumentType())
                .originalFileName(document.getOriginalFileName())
                .parsedTextLength(textLength)
                .maxUsableLength(maxDocumentLength)
                .willBeTruncated(textLength > maxDocumentLength)
                .createdAt(document.getCreatedAt())
                .build();
    }
}
