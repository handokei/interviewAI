package com.interviewai.backend.document.controller;

import com.interviewai.backend.common.response.ApiResponse;
import com.interviewai.backend.document.controller.dto.DocumentListResponseDto;
import com.interviewai.backend.document.controller.dto.DocumentUploadResponseDto;
import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Document", description = "문서 관리 API")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "이력서/포트폴리오 업로드")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentUploadResponseDto>> uploadDocument(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.uploadDocument(userId, file, documentType)));
    }

    @Operation(summary = "내 문서 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentListResponseDto>>> getMyDocuments(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.findMyDocuments(userId)));
    }

    @Operation(summary = "문서 삭제")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long documentId) {
        documentService.deleteDocument(userId, documentId);
        return ResponseEntity.ok(ApiResponse.ok(null, "문서가 삭제되었습니다."));
    }
}
