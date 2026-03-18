package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.ClaudeAiClient;
import com.interviewai.backend.client.GithubApiClient;
import com.interviewai.backend.client.JobCrawlerClient;
import com.interviewai.backend.common.exception.BusinessException;
import com.interviewai.backend.document.repository.UserDocumentRepository;
import com.interviewai.backend.interview.controller.dto.*;
import com.interviewai.backend.interview.enums.*;
import com.interviewai.backend.interview.model.InterviewFeedback;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewFeedbackRepository;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
import com.interviewai.backend.interview.repository.InterviewSessionRepository;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    @InjectMocks
    private InterviewServiceImpl interviewService;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewMessageRepository interviewMessageRepository;

    @Mock
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDocumentRepository userDocumentRepository;

    @Mock
    private ClaudeAiClient claudeAiClient;

    @Mock
    private GithubApiClient githubApiClient;

    @Mock
    private JobCrawlerClient jobCrawlerClient;

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
    @DisplayName("기능_테스트_기본_모드로_면접_세션을_생성한다")
    void 기능_테스트_기본_모드로_면접_세션을_생성한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "jobTitle", "백엔드 개발자");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .jobTitle("백엔드 개발자")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(claudeAiClient.chat(any(), any(), any())).willReturn("안녕하세요! 면접을 시작하겠습니다. 자기소개를 해주세요.");
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo(InterviewMode.BASIC);
        assertThat(response.getLevel()).isEqualTo(InterviewLevel.JUNIOR);
        assertThat(response.getFirstQuestion()).isNotBlank();
        verify(interviewSessionRepository).save(any(InterviewSession.class));
    }

    @Test
    @DisplayName("예외_테스트_이력서_모드에서_문서가_없으면_예외가_발생한다")
    void 예외_테스트_이력서_모드에서_문서가_없으면_예외가_발생한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.RESUME);
        setField(request, "level", InterviewLevel.JUNIOR);

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

        // when & then
        assertThatThrownBy(() -> interviewService.startInterview(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("문서");
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_면접_세션에_메시지를_전송하면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_면접_세션에_메시지를_전송하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "Java의 특징을 설명해주세요.");

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.sendMessage(userId, sessionId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("예외_테스트_완료된_면접_세션에_메시지를_전송하면_예외가_발생한다")
    void 예외_테스트_완료된_면접_세션에_메시지를_전송하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession completedSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        completedSession.complete();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(completedSession));

        // when & then
        assertThatThrownBy(() -> interviewService.sendMessage(userId, sessionId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("완료");
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_피드백을_조회하면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_피드백을_조회하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));
        given(interviewFeedbackRepository.findBySessionId(sessionId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.findFeedback(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("피드백");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
