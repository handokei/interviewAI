package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.ClaudeAiClient;
import com.interviewai.backend.client.GithubApiClient;
import com.interviewai.backend.client.JobCrawlerClient;
import com.interviewai.backend.common.exception.BusinessException;
import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.document.model.UserDocument;
import com.interviewai.backend.document.repository.UserDocumentRepository;
import com.interviewai.backend.interview.controller.dto.*;
import com.interviewai.backend.interview.enums.*;
import com.interviewai.backend.interview.model.InterviewFeedback;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.model.InterviewSessionDocument;
import com.interviewai.backend.interview.repository.InterviewFeedbackRepository;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
import com.interviewai.backend.interview.repository.InterviewSessionDocumentRepository;
import com.interviewai.backend.interview.repository.InterviewSessionRepository;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    @InjectMocks
    private InterviewServiceImpl interviewService;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewSessionDocumentRepository interviewSessionDocumentRepository;

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

    @Mock
    private InterviewMessageSaver interviewMessageSaver;

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
    @DisplayName("기능_테스트_이력서_모드에서_다중_문서로_면접_세션을_생성한다")
    void 기능_테스트_이력서_모드에서_다중_문서로_면접_세션을_생성한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.RESUME);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of(1L, 2L));

        UserDocument resume = mock(UserDocument.class);
        when(resume.getDocumentType()).thenReturn(DocumentType.RESUME);
        when(resume.getParsedText()).thenReturn("이력서 내용입니다.");

        UserDocument portfolio = mock(UserDocument.class);
        when(portfolio.getDocumentType()).thenReturn(DocumentType.PORTFOLIO);
        when(portfolio.getParsedText()).thenReturn("포트폴리오 내용입니다.");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.RESUME)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userDocumentRepository.findAllByIdInAndUserId(List.of(1L, 2L), userId))
                .willReturn(List.of(resume, portfolio));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(interviewSessionDocumentRepository.save(any(InterviewSessionDocument.class))).willReturn(null);
        given(claudeAiClient.chat(any(), any(), any())).willReturn("면접을 시작하겠습니다.");
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo(InterviewMode.RESUME);
        verify(interviewSessionDocumentRepository, times(2)).save(any(InterviewSessionDocument.class));
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
    @DisplayName("기능_테스트_면접_세션_목록을_페이지네이션으로_조회한다")
    void 기능_테스트_면접_세션_목록을_페이지네이션으로_조회한다() {
        // given
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .jobTitle("백엔드 개발자")
                .build();

        Page<InterviewSession> sessionPage = new PageImpl<>(List.of(session), pageable, 1);
        given(interviewSessionRepository.findByUserId(eq(userId), eq(pageable))).willReturn(sessionPage);

        // when
        Page<InterviewSessionResponseDto> result = interviewService.findMySessions(userId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getMode()).isEqualTo(InterviewMode.BASIC);
        assertThat(result.getContent().get(0).getLevel()).isEqualTo(InterviewLevel.JUNIOR);
    }

    @Test
    @DisplayName("기능_테스트_면접_세션이_없을_때_빈_페이지를_반환한다")
    void 기능_테스트_면접_세션이_없을_때_빈_페이지를_반환한다() {
        // given
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        given(interviewSessionRepository.findByUserId(eq(userId), eq(pageable)))
                .willReturn(Page.empty(pageable));

        // when
        Page<InterviewSessionResponseDto> result = interviewService.findMySessions(userId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
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

    @Test
    @DisplayName("기능_테스트_finishInterview_PASS_overallLevel로_피드백을_저장한다")
    void 기능_테스트_finishInterview_PASS_overallLevel로_피드백을_저장한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(claudeAiClient.generateFeedback(any(), any()))
                .willReturn("{\"overallLevel\":\"PASS\",\"strengths\":\"이해도가 높습니다.\",\"improvements\":\"없음\",\"fullReport\":\"훌륭합니다.\"}");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        InterviewFeedbackResponseDto result = interviewService.finishInterview(userId, sessionId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOverallLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(result.getStrengths()).isEqualTo("이해도가 높습니다.");
        verify(interviewFeedbackRepository).save(any(InterviewFeedback.class));
    }

    @Test
    @DisplayName("기능_테스트_finishInterview_STUDY_REQUIRED_overallLevel로_피드백을_저장한다")
    void 기능_테스트_finishInterview_STUDY_REQUIRED_overallLevel로_피드백을_저장한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.SENIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(claudeAiClient.generateFeedback(any(), any()))
                .willReturn("{\"overallLevel\":\"STUDY_REQUIRED\",\"strengths\":\"성실한 태도\",\"improvements\":\"기초 개념 보완 필요\",\"fullReport\":\"추가 학습이 필요합니다.\"}");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        InterviewFeedbackResponseDto result = interviewService.finishInterview(userId, sessionId);

        // then
        assertThat(result.getOverallLevel()).isEqualTo(AnswerLevel.STUDY_REQUIRED);
        assertThat(result.getImprovements()).isEqualTo("기초 개념 보완 필요");
    }

    @Test
    @DisplayName("기능_테스트_finishInterview_JSON_파싱_실패시_NEEDS_IMPROVEMENT_fallback으로_저장된다")
    void 기능_테스트_finishInterview_JSON_파싱_실패시_NEEDS_IMPROVEMENT_fallback으로_저장된다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(claudeAiClient.generateFeedback(any(), any())).willReturn("invalid json");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        InterviewFeedbackResponseDto result = interviewService.finishInterview(userId, sessionId);

        // then
        assertThat(result.getOverallLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
    }

    @Test
    @DisplayName("기능_테스트_finishInterview_잘못된_overallLevel_값이면_NEEDS_IMPROVEMENT_fallback으로_저장된다")
    void 기능_테스트_finishInterview_잘못된_overallLevel_값이면_NEEDS_IMPROVEMENT_fallback으로_저장된다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(claudeAiClient.generateFeedback(any(), any()))
                .willReturn("{\"overallLevel\":\"INVALID_VALUE\",\"strengths\":\"좋아요\",\"improvements\":\"없음\",\"fullReport\":\"좋습니다.\"}");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        InterviewFeedbackResponseDto result = interviewService.finishInterview(userId, sessionId);

        // then
        assertThat(result.getOverallLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(result.getStrengths()).isEqualTo("좋아요");
    }

    @Test
    @DisplayName("예외_테스트_finishInterview_이미_완료된_세션이면_예외가_발생한다")
    void 예외_테스트_finishInterview_이미_완료된_세션이면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession completedSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        completedSession.complete();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(completedSession));

        // when & then
        assertThatThrownBy(() -> interviewService.finishInterview(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("완료");
    }

    @Test
    @DisplayName("기능_테스트_진행_중인_면접_세션을_취소한다")
    void 기능_테스트_진행_중인_면접_세션을_취소한다() {
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

        // when
        interviewService.cancelInterview(userId, sessionId);

        // then
        assertThat(session.getStatus()).isEqualTo(com.interviewai.backend.interview.enums.InterviewStatus.CANCELLED);
    }

    @Test
    @DisplayName("예외_테스트_완료된_면접_세션을_취소하면_예외가_발생한다")
    void 예외_테스트_완료된_면접_세션을_취소하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        session.complete();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> interviewService.cancelInterview(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소");
    }

    @Test
    @DisplayName("예외_테스트_이미_취소된_면접_세션을_취소하면_예외가_발생한다")
    void 예외_테스트_이미_취소된_면접_세션을_취소하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        session.cancel();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> interviewService.cancelInterview(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소");
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_세션을_취소하면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_세션을_취소하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.cancelInterview(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("기능_테스트_진행_중인_면접_세션을_삭제한다")
    void 기능_테스트_진행_중인_면접_세션을_삭제한다() {
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

        // when
        interviewService.deleteInterview(userId, sessionId);

        // then
        verify(interviewMessageRepository).deleteBySessionId(sessionId);
        verify(interviewSessionDocumentRepository).deleteBySessionId(sessionId);
        verify(interviewFeedbackRepository).deleteBySessionId(sessionId);
        verify(interviewSessionRepository).delete(session);
    }

    @Test
    @DisplayName("기능_테스트_완료된_면접_세션을_삭제한다")
    void 기능_테스트_완료된_면접_세션을_삭제한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        session.complete();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));

        // when
        interviewService.deleteInterview(userId, sessionId);

        // then
        verify(interviewMessageRepository).deleteBySessionId(sessionId);
        verify(interviewSessionDocumentRepository).deleteBySessionId(sessionId);
        verify(interviewFeedbackRepository).deleteBySessionId(sessionId);
        verify(interviewSessionRepository).delete(session);
    }

    @Test
    @DisplayName("기능_테스트_취소된_면접_세션을_삭제한다")
    void 기능_테스트_취소된_면접_세션을_삭제한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        session.cancel();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));

        // when
        interviewService.deleteInterview(userId, sessionId);

        // then
        verify(interviewMessageRepository).deleteBySessionId(sessionId);
        verify(interviewSessionDocumentRepository).deleteBySessionId(sessionId);
        verify(interviewFeedbackRepository).deleteBySessionId(sessionId);
        verify(interviewSessionRepository).delete(session);
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_세션을_삭제하면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_세션을_삭제하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.deleteInterview(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("기능_테스트_인터뷰_통계를_조회한다")
    void 기능_테스트_인터뷰_통계를_조회한다() {
        // given
        Long userId = 1L;

        given(interviewSessionRepository.countByUserId(userId)).willReturn(10L);
        given(interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.COMPLETED)).willReturn(7L);
        given(interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.CANCELLED)).willReturn(2L);
        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.BASIC)).willReturn(5L);
        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.RESUME)).willReturn(3L);
        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.COMPANY)).willReturn(2L);

        given(interviewSessionRepository.countByUserIdAndLevel(userId, InterviewLevel.JUNIOR)).willReturn(6L);
        given(interviewSessionRepository.countByUserIdAndLevel(userId, InterviewLevel.SENIOR)).willReturn(4L);

        // when
        InterviewStatsResponseDto result = interviewService.getMyStats(userId);

        // then
        assertThat(result.getTotalCount()).isEqualTo(10L);
        assertThat(result.getCompletedCount()).isEqualTo(7L);
        assertThat(result.getCancelledCount()).isEqualTo(2L);
        assertThat(result.getModeDistribution()).containsEntry("BASIC", 5L);
        assertThat(result.getModeDistribution()).containsEntry("RESUME", 3L);
        assertThat(result.getModeDistribution()).containsEntry("COMPANY", 2L);
        assertThat(result.getLevelDistribution()).containsEntry("JUNIOR", 6L);
        assertThat(result.getLevelDistribution()).containsEntry("SENIOR", 4L);
    }

    @Test
    @DisplayName("기능_테스트_인터뷰_세션이_없을때_통계가_모두_0이다")
    void 기능_테스트_인터뷰_세션이_없을때_통계가_모두_0이다() {
        // given
        Long userId = 1L;

        given(interviewSessionRepository.countByUserId(userId)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.COMPLETED)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.CANCELLED)).willReturn(0L);

        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.BASIC)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.RESUME)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.COMPANY)).willReturn(0L);

        given(interviewSessionRepository.countByUserIdAndLevel(userId, InterviewLevel.JUNIOR)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndLevel(userId, InterviewLevel.SENIOR)).willReturn(0L);

        // when
        InterviewStatsResponseDto result = interviewService.getMyStats(userId);

        // then
        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getCompletedCount()).isZero();
    }

    @Test
    @DisplayName("기능_테스트_메시지_전송_시_AI_응답이_그대로_반환된다")
    void 기능_테스트_메시지_전송_시_AI_응답이_그대로_반환된다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "Java는 객체지향 언어입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        String aiResponse = "다음 질문입니다.";

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn(aiResponse, "{\"suggestFinish\":false,\"answerLevel\":\"NEEDS_IMPROVEMENT\",\"qualityHint\":\"좋은 답변이었습니다.\"}");

        // when
        InterviewSendMessageResponseDto response = interviewService.sendMessage(userId, sessionId, request);

        // then
        assertThat(response.getAiResponse()).isEqualTo(aiResponse);
        assertThat(response.isSuggestFinish()).isFalse();
        assertThat(response.getAnswerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(response.getQualityHint()).isEqualTo("좋은 답변이었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_AI_평가_결과_suggestFinish가_true면_true로_반환된다")
    void 기능_테스트_AI_평가_결과_suggestFinish가_true면_true로_반환된다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "마지막 답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("수고하셨습니다.", "{\"suggestFinish\":true,\"answerLevel\":\"PASS\",\"qualityHint\":\"전반적으로 훌륭했습니다.\"}");

        // when
        InterviewSendMessageResponseDto response = interviewService.sendMessage(userId, sessionId, request);

        // then
        assertThat(response.getAiResponse()).isEqualTo("수고하셨습니다.");
        assertThat(response.isSuggestFinish()).isTrue();
        assertThat(response.getAnswerLevel()).isEqualTo(AnswerLevel.PASS);
    }

    @Test
    @DisplayName("기능_테스트_AI_평가_JSON_파싱_실패시_fallback_값으로_반환된다")
    void 기능_테스트_AI_평가_JSON_파싱_실패시_fallback_값으로_반환된다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.SENIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("다음 질문입니다.", "invalid json response");

        // when
        InterviewSendMessageResponseDto response = interviewService.sendMessage(userId, sessionId, request);

        // then
        assertThat(response.isSuggestFinish()).isFalse();
        assertThat(response.getAnswerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(response.getQualityHint()).isEqualTo("답변이 접수되었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_buildEvalPrompt_레벨과_jobTitle이_포함된_프롬프트를_생성한다")
    void 기능_테스트_buildEvalPrompt_레벨과_jobTitle이_포함된_프롬프트를_생성한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .jobTitle("백엔드 개발자")
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("다음 질문입니다.", "{\"suggestFinish\":false,\"answerLevel\":\"NEEDS_IMPROVEMENT\",\"qualityHint\":\"좋았어요.\"}");

        // when & then (프롬프트 내용 검증은 실제 호출을 통해 간접 확인)
        InterviewSendMessageResponseDto response = interviewService.sendMessage(userId, sessionId, request);
        assertThat(response).isNotNull();
        verify(claudeAiClient, times(2)).chat(any(), any(), any());
    }

    @Test
    @DisplayName("기능_테스트_evaluateWithAi_정상_JSON_파싱된_결과를_반환한다")
    void 기능_테스트_evaluateWithAi_정상_JSON_파싱된_결과를_반환한다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("{\"suggestFinish\":true,\"answerLevel\":\"PASS\",\"qualityHint\":\"정확한 답변이었습니다.\"}");

        // when
        InterviewEvaluation eval = interviewService.evaluateWithAi(session, List.of(), "AI 응답");

        // then
        assertThat(eval.suggestFinish()).isTrue();
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(eval.qualityHint()).isEqualTo("정확한 답변이었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_여러_면접_세션을_일괄_삭제한다")
    void 기능_테스트_여러_면접_세션을_일괄_삭제한다() {
        // given
        Long userId = 1L;
        List<Long> sessionIds = List.of(1L, 2L, 3L);

        InterviewSession session1 = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();
        InterviewSession session2 = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.SENIOR).build();
        InterviewSession session3 = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.RESUME).level(InterviewLevel.JUNIOR).build();
        List<InterviewSession> sessions = List.of(session1, session2, session3);

        given(interviewSessionRepository.findAllByIdInAndUserId(sessionIds, userId)).willReturn(sessions);

        // when
        interviewService.deleteInterviews(userId, sessionIds);

        // then
        verify(interviewMessageRepository).deleteAllBySessionIdIn(sessionIds);
        verify(interviewSessionDocumentRepository).deleteAllBySessionIdIn(sessionIds);
        verify(interviewFeedbackRepository).deleteAllBySessionIdIn(sessionIds);
        verify(interviewSessionRepository).deleteAll(sessions);
    }

    @Test
    @DisplayName("기능_테스트_중복된_ID가_포함되어도_정상_삭제된다")
    void 기능_테스트_중복된_ID가_포함되어도_정상_삭제된다() {
        // given
        Long userId = 1L;
        List<Long> sessionIdsWithDuplicate = List.of(1L, 2L, 1L);
        List<Long> uniqueIds = List.of(1L, 2L);

        InterviewSession session1 = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();
        InterviewSession session2 = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.SENIOR).build();
        List<InterviewSession> sessions = List.of(session1, session2);

        given(interviewSessionRepository.findAllByIdInAndUserId(uniqueIds, userId)).willReturn(sessions);

        // when
        interviewService.deleteInterviews(userId, sessionIdsWithDuplicate);

        // then
        verify(interviewMessageRepository).deleteAllBySessionIdIn(uniqueIds);
        verify(interviewSessionDocumentRepository).deleteAllBySessionIdIn(uniqueIds);
        verify(interviewFeedbackRepository).deleteAllBySessionIdIn(uniqueIds);
        verify(interviewSessionRepository).deleteAll(sessions);
    }

    @Test
    @DisplayName("예외_테스트_다른_사용자의_세션이_포함되면_예외가_발생한다")
    void 예외_테스트_다른_사용자의_세션이_포함되면_예외가_발생한다() {
        // given
        Long userId = 1L;
        List<Long> sessionIds = List.of(1L, 2L, 3L);

        InterviewSession session1 = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(interviewSessionRepository.findAllByIdInAndUserId(sessionIds, userId))
                .willReturn(List.of(session1));

        // when & then
        assertThatThrownBy(() -> interviewService.deleteInterviews(userId, sessionIds))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_세션이_포함되면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_세션이_포함되면_예외가_발생한다() {
        // given
        Long userId = 1L;
        List<Long> sessionIds = List.of(1L, 999L);

        InterviewSession session1 = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(interviewSessionRepository.findAllByIdInAndUserId(sessionIds, userId))
                .willReturn(List.of(session1));

        // when & then
        assertThatThrownBy(() -> interviewService.deleteInterviews(userId, sessionIds))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("기능_테스트_기본_인터뷰_프롬프트에_호칭_지침이_포함된다")
    void 기능_테스트_기본_인터뷰_프롬프트에_호칭_지침이_포함된다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "jobTitle", "백엔드 개발자");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(claudeAiClient.chat(any(), any(), any())).willReturn("면접을 시작하겠습니다.");
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(claudeAiClient).chat(promptCaptor.capture(), any(), any());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("지원자의 이름이 제공된 문서");
        assertThat(prompt).doesNotContain("지원자 이름:");
    }

    @Test
    @DisplayName("기능_테스트_JUNIOR_레벨_프롬프트에_신입_개발자_설명이_포함된다")
    void 기능_테스트_JUNIOR_레벨_프롬프트에_신입_개발자_설명이_포함된다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.JUNIOR);

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(claudeAiClient.chat(any(), any(), any())).willReturn("면접을 시작하겠습니다.");
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(claudeAiClient).chat(promptCaptor.capture(), any(), any());
        assertThat(promptCaptor.getValue()).contains("신입 개발자");
    }

    @Test
    @DisplayName("기능_테스트_SENIOR_레벨_프롬프트에_경력_개발자_설명이_포함된다")
    void 기능_테스트_SENIOR_레벨_프롬프트에_경력_개발자_설명이_포함된다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.SENIOR);

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.SENIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(claudeAiClient.chat(any(), any(), any())).willReturn("면접을 시작하겠습니다.");
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(claudeAiClient).chat(promptCaptor.capture(), any(), any());
        assertThat(promptCaptor.getValue()).contains("경력 개발자");
    }

    @Test
    @DisplayName("기능_테스트_이력서_모드_프롬프트에_문서_내용이_포함된다")
    void 기능_테스트_이력서_모드_프롬프트에_문서_내용이_포함된다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.RESUME);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of(1L));

        UserDocument resume = mock(UserDocument.class);
        when(resume.getDocumentType()).thenReturn(DocumentType.RESUME);
        when(resume.getParsedText()).thenReturn("이력서 내용입니다.");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.RESUME).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userDocumentRepository.findAllByIdInAndUserId(List.of(1L), userId)).willReturn(List.of(resume));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(interviewSessionDocumentRepository.save(any(InterviewSessionDocument.class))).willReturn(null);
        given(claudeAiClient.chat(any(), any(), any())).willReturn("면접을 시작하겠습니다.");
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(claudeAiClient).chat(promptCaptor.capture(), any(), any());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("=== 지원자 이력서 ===");
        assertThat(prompt).contains("이력서 내용입니다.");
    }

    @Test
    @DisplayName("기능_테스트_streamMessage_호출_시_SseEmitter_를_반환한다")
    void 기능_테스트_streamMessage_호출_시_SseEmitter_를_반환한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "Java에 대해 설명해주세요.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        lenient().when(claudeAiClient.streamChat(any(), any(), any())).thenReturn(Flux.just("안녕", "하세요"));

        // when
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                interviewService.streamMessage(userId, sessionId, request);

        // then
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_세션에_streamMessage_호출_시_예외가_발생한다")
    void 예외_테스트_존재하지_않는_세션에_streamMessage_호출_시_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.streamMessage(userId, sessionId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("예외_테스트_완료된_세션에_streamMessage_호출_시_예외가_발생한다")
    void 예외_테스트_완료된_세션에_streamMessage_호출_시_예외가_발생한다() {
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
        assertThatThrownBy(() -> interviewService.streamMessage(userId, sessionId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("완료");
    }

    @Test
    @DisplayName("기능_테스트_sendTokenToEmitter_정상적으로_토큰을_전송한다")
    void 기능_테스트_sendTokenToEmitter_정상적으로_토큰을_전송한다() throws IOException {
        // given
        SseEmitter emitter = mock(SseEmitter.class);

        // when
        interviewService.sendTokenToEmitter(emitter, "안녕하세요");

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("예외_테스트_sendTokenToEmitter_IOException_발생_시_RuntimeException으로_래핑된다")
    void 예외_테스트_sendTokenToEmitter_IOException_발생_시_RuntimeException으로_래핑된다() throws IOException {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(IOException.class).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when & then
        assertThatThrownBy(() -> interviewService.sendTokenToEmitter(emitter, "토큰"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("기능_테스트_streamMessage_doOnComplete_완료_시_saveAiMessageAndComplete가_호출된다")
    void 기능_테스트_streamMessage_doOnComplete_완료_시_saveAiMessageAndComplete가_호출된다() throws InterruptedException {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(interviewMessageSaver).saveAiMessageAndComplete(any(), any(), any(), any());

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.streamChat(any(), any(), any())).willReturn(Flux.just("토큰"));
        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"NEEDS_IMPROVEMENT\",\"qualityHint\":\"좋은 답변이었습니다.\"}");

        // when
        interviewService.streamMessage(userId, sessionId, request);

        // then
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        verify(interviewMessageSaver).saveAiMessageAndComplete(any(), eq(sessionId), eq("토큰"), any(InterviewEvaluation.class));
    }

    @Test
    @DisplayName("기능_테스트_streamMessage_doOnComplete_저장_실패_시_emitter가_오류로_종료된다")
    void 기능_테스트_streamMessage_doOnComplete_저장_실패_시_emitter가_오류로_종료된다() throws InterruptedException {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            throw new RuntimeException("DB 오류");
        }).when(interviewMessageSaver).saveAiMessageAndComplete(any(), any(), any(), any());

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.streamChat(any(), any(), any())).willReturn(Flux.just("토큰"));
        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"NEEDS_IMPROVEMENT\",\"qualityHint\":\"좋은 답변이었습니다.\"}");

        // when
        interviewService.streamMessage(userId, sessionId, request);

        // then
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        verify(interviewMessageSaver).saveAiMessageAndComplete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("기능_테스트_streamMessage_Flux_오류_발생_시_emitter가_오류로_종료된다")
    void 기능_테스트_streamMessage_Flux_오류_발생_시_emitter가_오류로_종료된다() throws InterruptedException {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.streamChat(any(), any(), any()))
                .willReturn(Flux.error(new RuntimeException("스트림 오류")));

        // when
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                interviewService.streamMessage(userId, sessionId, request);

        // then
        Thread.sleep(200);
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("기능_테스트_streamMessage_streamChat_예외_발생_시_emitter가_오류로_종료된다")
    void 기능_테스트_streamMessage_streamChat_예외_발생_시_emitter가_오류로_종료된다() throws InterruptedException {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.streamChat(any(), any(), any()))
                .willThrow(new RuntimeException("API 오류"));

        // when
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                interviewService.streamMessage(userId, sessionId, request);

        // then
        Thread.sleep(200);
        assertThat(emitter).isNotNull();
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
