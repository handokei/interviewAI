package com.interviewai.backend.document.service;

import com.interviewai.backend.document.controller.dto.DocumentListResponseDto;
import com.interviewai.backend.document.controller.dto.DocumentUploadResponseDto;
import com.interviewai.backend.document.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentUploadResponseDto uploadDocument(Long userId, MultipartFile file, DocumentType documentType);

    List<DocumentListResponseDto> findMyDocuments(Long userId);

    void deleteDocument(Long userId, Long documentId);
}
