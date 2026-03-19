package com.interviewai.backend.document.service;

import com.interviewai.backend.client.PdfParserClient;
import com.interviewai.backend.common.exception.BusinessException;
import com.interviewai.backend.document.enums.DocumentErrorCode;
import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.document.model.UserDocument;
import com.interviewai.backend.document.repository.UserDocumentRepository;
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
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Mock
    private UserDocumentRepository userDocumentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PdfParserClient pdfParserClient;

    private User testUser;

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

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

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
                .parsedText("이력서 내용")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(pdfParserClient.parse(any())).willReturn("이력서 내용");
        given(userDocumentRepository.save(any(UserDocument.class))).willReturn(savedDocument);

        // when
        var response = documentService.uploadDocument(userId, pdfFile, DocumentType.RESUME);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getDocumentType()).isEqualTo(DocumentType.RESUME);
        assertThat(response.getOriginalFileName()).isEqualTo("resume.pdf");
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
    }
}
