package com.interviewai.backend.document.service;

import com.interviewai.backend.client.PdfParserClient;
import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.document.controller.dto.DocumentListResponseDto;
import com.interviewai.backend.document.enums.DocumentErrorCode;
import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.document.model.UserDocument;
import com.interviewai.backend.document.repository.UserDocumentRepository;
import com.interviewai.backend.interview.repository.InterviewSessionDocumentRepository;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Mock
    private UserDocumentRepository userDocumentRepository;

    @Mock
    private InterviewSessionDocumentRepository interviewSessionDocumentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PdfParserClient pdfParserClient;

    @Mock
    private InterviewProperties interviewProperties;

    private User testUser;
    private static final String LONG_PARSED_TEXT = "이력서 테스트 내용입니다. 이 텍스트는 50자 이상이어야 이미지 PDF 감지를 통과합니다. 충분한 길이의 텍스트를 생성합니다.";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("test@test.com")
                .name("테스트유저")
                .profileImageUrl(null)
                .role(UserRole.USER)
                .build();

        InterviewProperties.Prompt promptProperties = new InterviewProperties.Prompt();
        lenient().when(interviewProperties.getPrompt()).thenReturn(promptProperties);
    }

    @Test
    @DisplayName("예외_테스트_지원하지_않는_파일_형식으로_업로드하면_예외가_발생한다")
    void 예외_테스트_지원하지_않는_파일_형식으로_업로드하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "invalid content".getBytes()
        );

        // when & then
        assertThatThrownBy(() -> documentService.uploadDocument(userId, invalidFile, DocumentType.RESUME))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DocumentErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    @Test
    @DisplayName("기능_테스트_PDF_파일을_정상적으로_업로드한다")
    void 기능_테스트_PDF_파일을_정상적으로_업로드한다() throws Exception {
        // given
        Long userId = 1L;
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        UserDocument savedDocument = UserDocument.builder()
                .user(testUser)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.pdf")
                .parsedText(LONG_PARSED_TEXT)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(pdfParserClient.parse(any())).willReturn(LONG_PARSED_TEXT);
        given(userDocumentRepository.save(any(UserDocument.class))).willReturn(savedDocument);

        // when
        var response = documentService.uploadDocument(userId, pdfFile, DocumentType.RESUME);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getDocumentType()).isEqualTo(DocumentType.RESUME);
        assertThat(response.getOriginalFileName()).isEqualTo("resume.pdf");
        assertThat(response.getParsedTextLength()).isEqualTo(LONG_PARSED_TEXT.length());
        assertThat(response.getMaxUsableLength()).isEqualTo(5000);
    }

    @Test
    @DisplayName("기능_테스트_parsedText의_null_바이트를_제거하고_저장한다")
    void 기능_테스트_parsedText의_null_바이트를_제거하고_저장한다() throws Exception {
        // given
        Long userId = 1L;
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "pdf content".getBytes());
        String textWithNullBytes = "이력서\u0000내용입니다. 이 텍스트는 50자 이상이어야 이미지 PDF 감지를 통과합니다. 충분한 길이의 텍스트를 생성합니다.\u0000";
        String cleanedText = textWithNullBytes.replace("\u0000", "");
        UserDocument savedDocument = UserDocument.builder()
                .user(testUser)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.pdf")
                .parsedText(cleanedText)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(pdfParserClient.parse(any())).willReturn(textWithNullBytes);
        given(userDocumentRepository.save(any(UserDocument.class))).willReturn(savedDocument);

        // when
        documentService.uploadDocument(userId, pdfFile, DocumentType.RESUME);

        // then
        ArgumentCaptor<UserDocument> captor = ArgumentCaptor.forClass(UserDocument.class);
        verify(userDocumentRepository).save(captor.capture());
        assertThat(captor.getValue().getParsedText()).doesNotContain("\u0000");
    }

    @Test
    @DisplayName("기능_테스트_문서_목록을_페이지네이션으로_조회한다")
    void 기능_테스트_문서_목록을_페이지네이션으로_조회한다() {
        // given
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        UserDocument document = UserDocument.builder()
                .user(testUser)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.pdf")
                .parsedText("이력서 내용")
                .build();

        Page<UserDocument> documentPage = new PageImpl<>(List.of(document), pageable, 1);
        given(userDocumentRepository.findByUserId(eq(userId), eq(pageable))).willReturn(documentPage);

        // when
        Page<DocumentListResponseDto> result = documentService.findMyDocuments(userId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDocumentType()).isEqualTo(DocumentType.RESUME);
        assertThat(result.getContent().get(0).getOriginalFileName()).isEqualTo("resume.pdf");
    }

    @Test
    @DisplayName("기능_테스트_문서가_없을_때_빈_페이지를_반환한다")
    void 기능_테스트_문서가_없을_때_빈_페이지를_반환한다() {
        // given
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        given(userDocumentRepository.findByUserId(eq(userId), eq(pageable)))
                .willReturn(Page.empty(pageable));

        // when
        Page<DocumentListResponseDto> result = documentService.findMyDocuments(userId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("예외_테스트_이미지_PDF는_텍스트_추출_불가_에러를_반환한다")
    void 예외_테스트_이미지_PDF는_텍스트_추출_불가_에러를_반환한다() throws Exception {
        // given
        Long userId = 1L;
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "scanned.pdf", "application/pdf", "pdf content".getBytes());

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(pdfParserClient.parse(any())).willReturn("짧음");

        // when & then
        assertThatThrownBy(() -> documentService.uploadDocument(userId, pdfFile, DocumentType.RESUME))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DocumentErrorCode.IMAGE_PDF_NOT_SUPPORTED));
    }

    @Test
    @DisplayName("기능_테스트_면접에_사용된_문서를_삭제하면_링크를_먼저_unlink한_뒤_삭제한다")
    void 기능_테스트_면접에_사용된_문서를_삭제하면_링크를_먼저_unlink한_뒤_삭제한다() {
        // given
        Long userId = 1L;
        Long documentId = 1L;

        UserDocument document = UserDocument.builder()
                .user(testUser)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.pdf")
                .parsedText("이력서 내용")
                .build();

        given(userDocumentRepository.findByIdAndUserId(documentId, userId)).willReturn(Optional.of(document));

        // when
        documentService.deleteDocument(userId, documentId);

        // then — FK 위반 방지를 위해 링크 unlink가 문서 삭제보다 먼저 일어나야 한다
        InOrder inOrder = inOrder(interviewSessionDocumentRepository, userDocumentRepository);
        inOrder.verify(interviewSessionDocumentRepository).deleteByDocumentId(documentId);
        inOrder.verify(userDocumentRepository).delete(document);
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_문서를_삭제하면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_문서를_삭제하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long documentId = 999L;

        given(userDocumentRepository.findByIdAndUserId(documentId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> documentService.deleteDocument(userId, documentId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        // 소유권 검증 실패 시 unlink/delete는 일어나지 않아야 한다
        verifyNoInteractions(interviewSessionDocumentRepository);
    }

    @Test
    @DisplayName("기능_테스트_문서_유형을_변경한다")
    void 기능_테스트_문서_유형을_변경한다() {
        // given
        Long userId = 1L;
        Long documentId = 1L;

        UserDocument document = UserDocument.builder()
                .user(testUser)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.pdf")
                .parsedText("이력서 내용")
                .build();

        given(userDocumentRepository.findByIdAndUserId(documentId, userId)).willReturn(Optional.of(document));

        // when
        documentService.updateDocumentType(userId, documentId, DocumentType.PORTFOLIO);

        // then
        assertThat(document.getDocumentType()).isEqualTo(DocumentType.PORTFOLIO);
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_문서의_유형을_변경하면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_문서의_유형을_변경하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long documentId = 999L;

        given(userDocumentRepository.findByIdAndUserId(documentId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> documentService.updateDocumentType(userId, documentId, DocumentType.PORTFOLIO))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DocumentErrorCode.DOCUMENT_NOT_FOUND));
    }
}
