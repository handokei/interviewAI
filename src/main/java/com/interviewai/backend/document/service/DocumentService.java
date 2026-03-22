package com.interviewai.backend.document.service;

import com.interviewai.backend.document.controller.dto.DocumentListResponseDto;
import com.interviewai.backend.document.controller.dto.DocumentUploadResponseDto;
import com.interviewai.backend.document.enums.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    DocumentUploadResponseDto uploadDocument(Long userId, MultipartFile file, DocumentType documentType);

    Page<DocumentListResponseDto> findMyDocuments(Long userId, Pageable pageable);

    void deleteDocument(Long userId, Long documentId);
}
