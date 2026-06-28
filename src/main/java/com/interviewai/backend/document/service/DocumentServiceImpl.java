package com.interviewai.backend.document.service;

import com.interviewai.backend.client.PdfParserClient;
import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.document.controller.dto.DocumentListResponseDto;
import com.interviewai.backend.document.controller.dto.DocumentUploadResponseDto;
import com.interviewai.backend.document.enums.DocumentErrorCode;
import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.document.model.UserDocument;
import com.interviewai.backend.document.repository.UserDocumentRepository;
import com.interviewai.backend.user.enums.UserErrorCode;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    private final UserDocumentRepository userDocumentRepository;
    private final UserRepository userRepository;
    private final PdfParserClient pdfParserClient;
    private final InterviewProperties interviewProperties;

    @Override
    @Transactional
    public DocumentUploadResponseDto uploadDocument(Long userId, MultipartFile file, DocumentType documentType) {
        validatePdfFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String parsedText;
        try {
            parsedText = pdfParserClient.parse(file).replace("\u0000", "");
        } catch (Throwable e) {
            log.error("PDF 파싱 실패: {}", e.getMessage(), e);
            throw new BusinessException(DocumentErrorCode.FILE_PARSE_FAILED);
        }

        if (parsedText.length() < 50) {
            throw new BusinessException(DocumentErrorCode.IMAGE_PDF_NOT_SUPPORTED);
        }

        UserDocument document = UserDocument.builder()
                .user(user)
                .documentType(documentType)
                .originalFileName(file.getOriginalFilename())
                .parsedText(parsedText)
                .build();

        return DocumentUploadResponseDto.from(
                userDocumentRepository.save(document),
                interviewProperties.getPrompt().getMaxDocumentLength()
        );
    }

    @Override
    public Page<DocumentListResponseDto> findMyDocuments(Long userId, Pageable pageable) {
        return userDocumentRepository.findByUserId(userId, pageable)
                .map(DocumentListResponseDto::from);
    }

    @Override
    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        UserDocument document = userDocumentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        userDocumentRepository.delete(document);
    }

    @Override
    @Transactional
    public void updateDocumentType(Long userId, Long documentId, DocumentType documentType) {
        UserDocument document = userDocumentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        document.updateDocumentType(documentType);
    }

    private void validatePdfFile(MultipartFile file) {
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        boolean isPdf = MediaType.APPLICATION_PDF_VALUE.equals(contentType)
                || (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf"));
        if (!isPdf) {
            throw new BusinessException(DocumentErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }
}
