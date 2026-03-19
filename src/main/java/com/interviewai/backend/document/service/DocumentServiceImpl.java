package com.interviewai.backend.document.service;

import com.interviewai.backend.client.PdfParserClient;
import com.interviewai.backend.common.exception.BusinessException;
import com.interviewai.backend.document.controller.dto.DocumentListResponseDto;
import com.interviewai.backend.document.controller.dto.DocumentUploadResponseDto;
import com.interviewai.backend.document.enums.DocumentErrorCode;
import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.document.model.UserDocument;
import com.interviewai.backend.document.repository.UserDocumentRepository;
import com.interviewai.backend.user.enums.UserErrorCode;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final UserDocumentRepository userDocumentRepository;
    private final UserRepository userRepository;
    private final PdfParserClient pdfParserClient;

    @Override
    @Transactional
    public DocumentUploadResponseDto uploadDocument(Long userId, MultipartFile file, DocumentType documentType) {
        validatePdfFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String parsedText;
        try {
            parsedText = pdfParserClient.parse(file);
        } catch (Exception e) {
            log.error("PDF 파싱 실패: {}", e.getMessage());
            throw new BusinessException(DocumentErrorCode.FILE_PARSE_FAILED);
        }

        UserDocument document = UserDocument.builder()
                .user(user)
                .documentType(documentType)
                .originalFileName(file.getOriginalFilename())
                .parsedText(parsedText)
                .build();

        return DocumentUploadResponseDto.from(userDocumentRepository.save(document));
    }

    @Override
    public List<DocumentListResponseDto> findMyDocuments(Long userId) {
        return userDocumentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(DocumentListResponseDto::from)
                .toList();
    }

    @Override
    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        UserDocument document = userDocumentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        userDocumentRepository.delete(document);
    }

    private void validatePdfFile(MultipartFile file) {
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        boolean isPdf = PDF_CONTENT_TYPE.equals(contentType)
                || (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf"));
        if (!isPdf) {
            throw new BusinessException(DocumentErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }
}
