package com.interviewai.backend.document.controller.dto;

import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.document.model.UserDocument;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentListResponseDto {

    private Long id;
    private DocumentType documentType;
    private String originalFileName;
    private LocalDateTime createdAt;

    public static DocumentListResponseDto from(UserDocument document) {
        return DocumentListResponseDto.builder()
                .id(document.getId())
                .documentType(document.getDocumentType())
                .originalFileName(document.getOriginalFileName())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
