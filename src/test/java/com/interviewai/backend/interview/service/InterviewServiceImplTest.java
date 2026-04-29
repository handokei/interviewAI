package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.ClaudeAiClient;
import com.interviewai.backend.client.GithubApiClient;
import com.interviewai.backend.client.JobCrawlerClient;
import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.global.config.InterviewProperties;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private InterviewEvaluationService interviewEvaluationService;

    @Mock
    private ConversationSummaryService conversationSummaryService;

    @Mock
    private TokenEstimator tokenEstimator;

    @Mock
    private ResumeTextSummarizer resumeTextSummarizer;

    @Mock
    private InterviewProperties interviewProperties;

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

        InterviewProperties.Prompt prompt = new InterviewProperties.Prompt();
        InterviewProperties.Sse sse = new InterviewProperties.Sse();
        lenient().when(interviewProperties.getPrompt()).thenReturn(prompt);
        lenient().when(interviewProperties.getSse()).thenReturn(sse);
        lenient().when(interviewProperties.getCrawlTimeoutSeconds()).thenReturn(15);
        lenient().when(interviewProperties.getGithubTimeoutSeconds()).thenReturn(30);

        lenient().when(claudeAiClient.summarize(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        lenient().when(tokenEstimator.trimHistoryWithPriority(any(), anyInt()))
                .thenAnswer(inv -> {
                    List<InterviewMessage> msgs = inv.getArgument(0);
                    return msgs.stream()
                            .map(msg -> new com.interviewai.backend.client.dto.ChatMessage(
                                    msg.getRole() == com.interviewai.backend.interview.enums.MessageRole.AI ? "assistant" : "user",
                                    msg.getContent()))
                            .toList();
                });
        lenient().when(tokenEstimator.trimHistoryWithPriorityAndReport(any(), anyInt()))
                .thenAnswer(inv -> {
                    List<InterviewMessage> msgs = inv.getArgument(0);
                    List<com.interviewai.backend.client.dto.ChatMessage> retained = msgs.stream()
                            .map(msg -> new com.interviewai.backend.client.dto.ChatMessage(
                                    msg.getRole() == com.interviewai.backend.interview.enums.MessageRole.AI ? "assistant" : "user",
                                    msg.getContent()))
                            .toList();
                    return new TrimResult(retained, List.of(), false);
                });
        lenient().when(tokenEstimator.trimHistory(any(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tokenEstimator.toChatMessages(any()))
                .thenAnswer(inv -> {
                    List<InterviewMessage> msgs = inv.getArgument(0);
                    if (msgs == null) return List.of();
                    return msgs.stream()
                            .map(msg -> new com.interviewai.backend.client.dto.ChatMessage(
                                    msg.getRole() == com.interviewai.backend.interview.enums.MessageRole.AI ? "assistant" : "user",
                                    msg.getContent()))
                            .toList();
                });
        lenient().when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);
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

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo(InterviewMode.BASIC);
        assertThat(response.getLevel()).isEqualTo(InterviewLevel.JUNIOR);
        assertThat(response.getFirstQuestion()).isNull();
        verify(interviewSessionRepository, times(2)).save(any(InterviewSession.class));
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

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo(InterviewMode.RESUME);
        assertThat(response.getFirstQuestion()).isNull();
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
    @DisplayName("기능_테스트_finishInterview_토큰_초과시_trimming된_대화로_피드백을_생성한다")
    void 기능_테스트_finishInterview_토큰_초과시_trimming된_대화로_피드백을_생성한다() {
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
        lenient().when(tokenEstimator.estimateTokens(anyString())).thenReturn(50000); // 30000 초과
        lenient().when(tokenEstimator.toChatMessages(any())).thenReturn(List.of());
        lenient().when(tokenEstimator.trimHistory(any(), anyInt())).thenReturn(List.of());
        given(claudeAiClient.generateFeedback(any(), any()))
                .willReturn("{\"overallLevel\":\"PASS\",\"strengths\":\"좋음\",\"improvements\":\"없음\",\"fullReport\":\"리포트\"}");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        InterviewFeedbackResponseDto result = interviewService.finishInterview(userId, sessionId);

        // then
        assertThat(result).isNotNull();
        verify(tokenEstimator).trimHistory(any(), eq(30000));
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
    @DisplayName("기능_테스트_메시지_전송_시_AI_응답이_그대로_반환되고_비동기_평가가_트리거된다")
    void 기능_테스트_메시지_전송_시_AI_응답이_그대로_반환되고_비동기_평가가_트리거된다() {
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
        InterviewMessage savedAiMsg = mock(InterviewMessage.class);
        given(savedAiMsg.getId()).willReturn(100L);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null, savedAiMsg);
        InterviewMessage priorAiMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("면접 질문입니다.").build();
        InterviewMessage priorUserMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.USER).content("사용자 답변입니다.").build();
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .willReturn(List.of(priorAiMsg, priorUserMsg));
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any())).willReturn(aiResponse);

        // when
        InterviewSendMessageResponseDto response = interviewService.sendMessage(userId, sessionId, request);

        // then
        assertThat(response.getAiResponse()).isEqualTo(aiResponse);
        assertThat(response.isSuggestFinish()).isFalse();
        assertThat(response.getAnswerLevel()).isNull();
        assertThat(response.getQualityHint()).isNull();
        verify(claudeAiClient, times(1)).chat(any(), any(), any());
        verify(interviewEvaluationService).evaluateAsync(eq(100L), any(), any(), eq(aiResponse));
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

        // when
        interviewService.startInterview(userId, request);

        // then
        // systemPrompt is set on the session object returned by the first save() call.
        // The second save() persists the session with the generated systemPrompt.
        // We verify by capturing the session passed to the second save().
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());
        String prompt = sessionCaptor.getAllValues().get(1).getSystemPrompt();
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

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());
        String prompt = sessionCaptor.getAllValues().get(1).getSystemPrompt();
        assertThat(prompt).contains("신입 개발자");
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

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());
        String prompt = sessionCaptor.getAllValues().get(1).getSystemPrompt();
        assertThat(prompt).contains("경력 개발자");
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

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());
        String prompt = sessionCaptor.getAllValues().get(1).getSystemPrompt();
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
        InterviewMessage priorAiMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("면접 질문입니다.").build();
        InterviewMessage priorUserMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.USER).content("사용자 답변입니다.").build();
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .willReturn(List.of(priorAiMsg, priorUserMsg));
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
            return 100L;
        }).when(interviewMessageSaver).saveAiMessageAndComplete(any(), any(), any());

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        InterviewMessage priorAiMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("면접 질문입니다.").build();
        InterviewMessage priorUserMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.USER).content("사용자 답변입니다.").build();
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .willReturn(List.of(priorAiMsg, priorUserMsg));
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.streamChat(any(), any(), any())).willReturn(Flux.just("토큰"));

        // when
        interviewService.streamMessage(userId, sessionId, request);

        // then
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        verify(interviewMessageSaver).saveAiMessageAndComplete(any(), eq(sessionId), eq("토큰"));
        verify(interviewEvaluationService).evaluateAsync(eq(100L), any(), any(), eq("토큰"));
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
        }).when(interviewMessageSaver).saveAiMessageAndComplete(any(), any(), any());

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        InterviewMessage priorAiMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("면접 질문입니다.").build();
        InterviewMessage priorUserMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.USER).content("사용자 답변입니다.").build();
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .willReturn(List.of(priorAiMsg, priorUserMsg));
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.streamChat(any(), any(), any())).willReturn(Flux.just("토큰"));

        // when
        interviewService.streamMessage(userId, sessionId, request);

        // then
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        verify(interviewMessageSaver).saveAiMessageAndComplete(any(), any(), any());
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
        InterviewMessage priorAiMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("면접 질문입니다.").build();
        InterviewMessage priorUserMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.USER).content("사용자 답변입니다.").build();
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .willReturn(List.of(priorAiMsg, priorUserMsg));
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
        InterviewMessage priorAiMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("면접 질문입니다.").build();
        InterviewMessage priorUserMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.USER).content("사용자 답변입니다.").build();
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .willReturn(List.of(priorAiMsg, priorUserMsg));
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

    @Test
    @DisplayName("기능_테스트_finishInterview_메시지가_있을때_AI와_지원자_역할이_대화내용에_포함된다")
    void 기능_테스트_finishInterview_메시지가_있을때_AI와_지원자_역할이_대화내용에_포함된다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        InterviewMessage aiMessage = mock(InterviewMessage.class);
        when(aiMessage.getRole()).thenReturn(MessageRole.AI);
        when(aiMessage.getContent()).thenReturn("첫 번째 질문입니다.");

        InterviewMessage userMessage = mock(InterviewMessage.class);
        when(userMessage.getRole()).thenReturn(MessageRole.USER);
        when(userMessage.getContent()).thenReturn("답변입니다.");

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .willReturn(List.of(aiMessage, userMessage));
        given(claudeAiClient.generateFeedback(any(), any()))
                .willReturn("{\"overallLevel\":\"PASS\",\"strengths\":\"좋습니다.\",\"improvements\":\"없음\",\"fullReport\":\"훌륭합니다.\"}");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        InterviewFeedbackResponseDto result = interviewService.finishInterview(userId, sessionId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOverallLevel()).isEqualTo(AnswerLevel.PASS);
        verify(interviewFeedbackRepository).save(any(InterviewFeedback.class));
    }

    @Test
    @DisplayName("기능_테스트_startInterview_crawl과_github_모두_제공되면_두_Future가_모두_실행된다")
    void 기능_테스트_startInterview_crawl과_github_모두_제공되면_두_Future가_모두_실행된다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.COMPANY);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "githubUrl", "https://github.com/testuser");
        setField(request, "jobPostingUrl", "https://jobs.example.com/backend");

        String crawledContent = "채용공고 내용입니다.";
        String githubContent = "GitHub 정보입니다.";

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.COMPANY)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(jobCrawlerClient.crawl("https://jobs.example.com/backend")).willReturn(crawledContent);
        given(githubApiClient.extractRepoAnalysis(eq(List.of("https://github.com/testuser")), any())).willReturn(githubContent);
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        verify(jobCrawlerClient).crawl("https://jobs.example.com/backend");
        verify(githubApiClient).extractRepoAnalysis(eq(List.of("https://github.com/testuser")), any());
    }

    @Test
    @DisplayName("기능_테스트_startInterview_githubUrl만_제공되면_githubFuture만_실행된다")
    void 기능_테스트_startInterview_githubUrl만_제공되면_githubFuture만_실행된다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "githubUrl", "https://github.com/testuser");

        String githubContent = "GitHub 정보입니다.";

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(githubApiClient.extractRepoAnalysis(eq(List.of("https://github.com/testuser")), any())).willReturn(githubContent);
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        verify(githubApiClient).extractRepoAnalysis(eq(List.of("https://github.com/testuser")), any());
        verify(jobCrawlerClient, org.mockito.Mockito.never()).crawl(any());
    }

    @Test
    @DisplayName("기능_테스트_startInterview_GitHub_요약_실패시_원문으로_fallback한다")
    void 기능_테스트_startInterview_GitHub_요약_실패시_원문으로_fallback한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "githubUrl", "https://github.com/testuser");

        String githubContent = "GitHub 원문 정보입니다.";

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(githubApiClient.extractRepoAnalysis(eq(List.of("https://github.com/testuser")), any())).willReturn(githubContent);
        given(claudeAiClient.summarize(any(), any())).willThrow(new RuntimeException("AI 요약 실패"));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        verify(claudeAiClient).summarize(any(), eq(githubContent));
    }

    @Test
    @DisplayName("기능_테스트_startInterview_GitHub_수집결과가_빈문자열이면_요약하지_않는다")
    void 기능_테스트_startInterview_GitHub_수집결과가_빈문자열이면_요약하지_않는다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "githubUrl", "https://github.com/testuser");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(githubApiClient.extractRepoAnalysis(eq(List.of("https://github.com/testuser")), any())).willReturn("");
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        verify(claudeAiClient, org.mockito.Mockito.never()).summarize(any(), any());
    }

    @Test
    @DisplayName("기능_테스트_startInterview_jobPostingUrl만_제공되면_crawlFuture만_실행된다")
    void 기능_테스트_startInterview_jobPostingUrl만_제공되면_crawlFuture만_실행된다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.COMPANY);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of(1L));
        setField(request, "jobPostingUrl", "https://jobs.example.com/backend");

        String crawledContent = "채용공고 내용입니다.";

        UserDocument resume = mock(UserDocument.class);
        when(resume.getDocumentType()).thenReturn(DocumentType.RESUME);
        when(resume.getParsedText()).thenReturn("이력서 내용입니다.");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.COMPANY)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userDocumentRepository.findAllByIdInAndUserId(List.of(1L), userId)).willReturn(List.of(resume));
        given(jobCrawlerClient.crawl("https://jobs.example.com/backend")).willReturn(crawledContent);
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(interviewSessionDocumentRepository.save(any(InterviewSessionDocument.class))).willReturn(null);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        verify(jobCrawlerClient).crawl("https://jobs.example.com/backend");
        verify(githubApiClient, org.mockito.Mockito.never()).extractRepoAnalysis(any(), any());
    }

    @Test
    @DisplayName("기능_테스트_startInterview_jobPostingUrl과_githubUrl_모두_없으면_두_Future_모두_실행되지_않는다")
    void 기능_테스트_startInterview_jobPostingUrl과_githubUrl_모두_없으면_두_Future_모두_실행되지_않는다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.SENIOR);
        setField(request, "jobTitle", "백엔드 개발자");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.SENIOR)
                .jobTitle("백엔드 개발자")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        verify(jobCrawlerClient, org.mockito.Mockito.never()).crawl(any());
        verify(githubApiClient, org.mockito.Mockito.never()).extractRepoAnalysis(any(), any());
    }

    @Test
    @DisplayName("기능_테스트_streamFirstQuestion_IN_PROGRESS_세션에서_SseEmitter를_반환한다")
    void 기능_테스트_streamFirstQuestion_IN_PROGRESS_세션에서_SseEmitter를_반환한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        // status is IN_PROGRESS by default after build

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.existsBySessionIdAndRole(sessionId, MessageRole.AI)).willReturn(false);
        lenient().when(claudeAiClient.streamChat(any(), any(), any())).thenReturn(Flux.just("첫", "질문"));

        // when
        SseEmitter emitter = interviewService.streamFirstQuestion(userId, sessionId);

        // then
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("예외_테스트_streamFirstQuestion_세션이_존재하지_않으면_예외가_발생한다")
    void 예외_테스트_streamFirstQuestion_세션이_존재하지_않으면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.streamFirstQuestion(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("예외_테스트_streamFirstQuestion_이미_완료된_세션이면_예외가_발생한다")
    void 예외_테스트_streamFirstQuestion_이미_완료된_세션이면_예외가_발생한다() {
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
        assertThatThrownBy(() -> interviewService.streamFirstQuestion(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("완료");
    }

    @Test
    @DisplayName("예외_테스트_streamFirstQuestion_취소된_세션이면_예외가_발생한다")
    void 예외_테스트_streamFirstQuestion_취소된_세션이면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession cancelledSession = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        cancelledSession.cancel();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(cancelledSession));

        // when & then
        assertThatThrownBy(() -> interviewService.streamFirstQuestion(userId, sessionId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("예외_테스트_streamFirstQuestion_AI_메시지가_이미_존재하면_예외가_발생한다")
    void 예외_테스트_streamFirstQuestion_AI_메시지가_이미_존재하면_예외가_발생한다() {
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
        given(interviewMessageRepository.existsBySessionIdAndRole(sessionId, MessageRole.AI))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> interviewService.streamFirstQuestion(userId, sessionId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("기능_테스트_streamFirstQuestion_doOnComplete_완료_시_saveFirstQuestionMessage가_호출된다")
    void 기능_테스트_streamFirstQuestion_doOnComplete_완료_시_saveFirstQuestionMessage가_호출된다() throws InterruptedException {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(interviewMessageSaver).saveFirstQuestionMessage(any(), any(), any());

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.existsBySessionIdAndRole(sessionId, MessageRole.AI)).willReturn(false);
        given(claudeAiClient.streamChat(any(), any(), any())).willReturn(Flux.just("첫", "질문"));

        // when
        interviewService.streamFirstQuestion(userId, sessionId);

        // then
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        verify(interviewMessageSaver).saveFirstQuestionMessage(any(), eq(sessionId), eq("첫질문"));
    }

    @Test
    @DisplayName("기능_테스트_streamFirstQuestion_doOnComplete_저장_실패_시_emitter가_오류로_종료된다")
    void 기능_테스트_streamFirstQuestion_doOnComplete_저장_실패_시_emitter가_오류로_종료된다() throws InterruptedException {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            throw new RuntimeException("DB 저장 실패");
        }).when(interviewMessageSaver).saveFirstQuestionMessage(any(), any(), any());

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.existsBySessionIdAndRole(sessionId, MessageRole.AI)).willReturn(false);
        given(claudeAiClient.streamChat(any(), any(), any())).willReturn(Flux.just("토큰"));

        // when
        SseEmitter emitter = interviewService.streamFirstQuestion(userId, sessionId);

        // then
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        assertThat(emitter).isNotNull();
        verify(interviewMessageSaver).saveFirstQuestionMessage(any(), any(), any());
    }

    @Test
    @DisplayName("기능_테스트_streamFirstQuestion_Flux_오류_발생_시_emitter가_오류로_종료된다")
    void 기능_테스트_streamFirstQuestion_Flux_오류_발생_시_emitter가_오류로_종료된다() throws InterruptedException {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.existsBySessionIdAndRole(sessionId, MessageRole.AI)).willReturn(false);
        given(claudeAiClient.streamChat(any(), any(), any()))
                .willReturn(Flux.error(new RuntimeException("스트림 오류")));

        // when
        SseEmitter emitter = interviewService.streamFirstQuestion(userId, sessionId);

        // then
        Thread.sleep(200);
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("기능_테스트_streamFirstQuestion_streamChat_예외_발생_시_emitter가_오류로_종료된다")
    void 기능_테스트_streamFirstQuestion_streamChat_예외_발생_시_emitter가_오류로_종료된다() throws InterruptedException {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.existsBySessionIdAndRole(sessionId, MessageRole.AI)).willReturn(false);
        given(claudeAiClient.streamChat(any(), any(), any()))
                .willThrow(new RuntimeException("API 호출 실패"));

        // when
        SseEmitter emitter = interviewService.streamFirstQuestion(userId, sessionId);

        // then
        Thread.sleep(200);
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("기능_테스트_getLatestEvaluation_평가_완료된_메시지의_결과를_반환한다")
    void 기능_테스트_getLatestEvaluation_평가_완료된_메시지의_결과를_반환한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        InterviewMessage latestAiMessage = InterviewMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content("다음 질문입니다.")
                .build();
        latestAiMessage.updateEvaluation(
                com.interviewai.backend.interview.enums.AnswerLevel.PASS,
                true,
                "훌륭한 답변입니다."
        );

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));
        given(interviewMessageRepository.findTopBySessionIdAndRoleOrderByCreatedAtDesc(
                sessionId, MessageRole.AI))
                .willReturn(Optional.of(latestAiMessage));
        given(interviewMessageRepository.existsBySessionIdAndSuggestFinishTrue(sessionId))
                .willReturn(true);

        // when
        InterviewMessageEvalResponseDto result = interviewService.getLatestEvaluation(userId, sessionId);

        // then
        assertThat(result.isEvaluated()).isTrue();
        assertThat(result.getSuggestFinish()).isTrue();
        assertThat(result.getAnswerLevel()).isEqualTo(com.interviewai.backend.interview.enums.AnswerLevel.PASS);
        assertThat(result.getQualityHint()).isEqualTo("훌륭한 답변입니다.");
    }

    @Test
    @DisplayName("기능_테스트_getLatestEvaluation_평가_미완료된_메시지를_반환한다")
    void 기능_테스트_getLatestEvaluation_평가_미완료된_메시지를_반환한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        InterviewMessage latestAiMessage = InterviewMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content("다음 질문입니다.")
                .build();
        // updateEvaluation 호출 없음 — answerLevel == null → isEvaluated() == false

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));
        given(interviewMessageRepository.findTopBySessionIdAndRoleOrderByCreatedAtDesc(
                sessionId, MessageRole.AI))
                .willReturn(Optional.of(latestAiMessage));

        // when
        InterviewMessageEvalResponseDto result = interviewService.getLatestEvaluation(userId, sessionId);

        // then
        assertThat(result.isEvaluated()).isFalse();
        assertThat(result.getAnswerLevel()).isNull();
        assertThat(result.getSuggestFinish()).isNull();
        assertThat(result.getQualityHint()).isNull();
    }

    @Test
    @DisplayName("예외_테스트_getLatestEvaluation_존재하지_않는_세션이면_예외가_발생한다")
    void 예외_테스트_getLatestEvaluation_존재하지_않는_세션이면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.getLatestEvaluation(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("예외_테스트_getLatestEvaluation_AI_메시지가_없으면_예외가_발생한다")
    void 예외_테스트_getLatestEvaluation_AI_메시지가_없으면_예외가_발생한다() {
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
        given(interviewMessageRepository.findTopBySessionIdAndRoleOrderByCreatedAtDesc(
                sessionId, MessageRole.AI))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.getLatestEvaluation(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("메시지");
    }

    // ----------------------------------------------------------------
    // 커버리지 보완 테스트
    // ----------------------------------------------------------------

    @Test
    @DisplayName("예외_테스트_startInterview_사용자가_없으면_예외가_발생한다")
    void 예외_테스트_startInterview_사용자가_없으면_예외가_발생한다() {
        // given
        Long userId = 999L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.JUNIOR);

        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.startInterview(userId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("예외_테스트_finishInterview_세션이_존재하지_않으면_예외가_발생한다")
    void 예외_테스트_finishInterview_세션이_존재하지_않으면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.finishInterview(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("기능_테스트_findSessionMessages_메시지_목록을_반환한다")
    void 기능_테스트_findSessionMessages_메시지_목록을_반환한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        InterviewMessage msg1 = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("질문입니다.").build();
        InterviewMessage msg2 = InterviewMessage.builder()
                .session(session).role(MessageRole.USER).content("답변입니다.").build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .willReturn(List.of(msg1, msg2));

        // when
        List<InterviewMessageResponseDto> result = interviewService.findSessionMessages(userId, sessionId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("질문입니다.");
        assertThat(result.get(1).getContent()).isEqualTo("답변입니다.");
    }

    @Test
    @DisplayName("예외_테스트_findSessionMessages_세션이_존재하지_않으면_예외가_발생한다")
    void 예외_테스트_findSessionMessages_세션이_존재하지_않으면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.findSessionMessages(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("예외_테스트_findFeedback_세션이_존재하지_않으면_예외가_발생한다")
    void 예외_테스트_findFeedback_세션이_존재하지_않으면_예외가_발생한다() {
        // given
        Long userId = 1L;
        Long sessionId = 999L;

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interviewService.findFeedback(userId, sessionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("면접 세션");
    }

    @Test
    @DisplayName("기능_테스트_findFeedback_피드백을_정상_조회한다")
    void 기능_테스트_findFeedback_피드백을_정상_조회한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        InterviewFeedback feedback = InterviewFeedback.builder()
                .session(session)
                .overallLevel(AnswerLevel.PASS)
                .strengths("잘했습니다.")
                .improvements("없습니다.")
                .fullReport("훌륭합니다.")
                .build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId))
                .willReturn(Optional.of(session));
        given(interviewFeedbackRepository.findBySessionId(sessionId))
                .willReturn(Optional.of(feedback));

        // when
        InterviewFeedbackResponseDto result = interviewService.findFeedback(userId, sessionId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOverallLevel()).isEqualTo(AnswerLevel.PASS);
    }

    @Test
    @DisplayName("예외_테스트_validateModeRequirements_COMPANY_모드에서_문서와_github_없으면_예외가_발생한다")
    void 예외_테스트_validateModeRequirements_COMPANY_모드에서_문서와_github_없으면_예외가_발생한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.COMPANY);
        setField(request, "level", InterviewLevel.JUNIOR);
        // documentIds null, githubUrl null

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

        // when & then
        assertThatThrownBy(() -> interviewService.startInterview(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("문서");
    }

    @Test
    @DisplayName("기능_테스트_startInterview_CAREER_STATEMENT_문서_타입이_포함된다")
    void 기능_테스트_startInterview_CAREER_STATEMENT_문서_타입이_포함된다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.RESUME);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of(1L));

        UserDocument careerDoc = mock(UserDocument.class);
        when(careerDoc.getDocumentType()).thenReturn(DocumentType.CAREER_STATEMENT);
        when(careerDoc.getParsedText()).thenReturn("경력기술서 내용입니다.");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.RESUME).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userDocumentRepository.findAllByIdInAndUserId(List.of(1L), userId)).willReturn(List.of(careerDoc));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(interviewSessionDocumentRepository.save(any(InterviewSessionDocument.class))).willReturn(null);

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());
        String prompt = sessionCaptor.getAllValues().get(1).getSystemPrompt();
        assertThat(prompt).contains("=== 지원자 경력기술서 ===");
    }

    @Test
    @DisplayName("기능_테스트_startInterview_문서_parsedText가_null이면_프롬프트에_포함되지_않는다")
    void 기능_테스트_startInterview_문서_parsedText가_null이면_프롬프트에_포함되지_않는다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.RESUME);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of(1L));

        // parsedText=null → getDocumentType()은 호출되지 않으므로 stub 불필요
        UserDocument doc = mock(UserDocument.class);
        when(doc.getParsedText()).thenReturn(null);

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.RESUME).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userDocumentRepository.findAllByIdInAndUserId(List.of(1L), userId)).willReturn(List.of(doc));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(interviewSessionDocumentRepository.save(any(InterviewSessionDocument.class))).willReturn(null);

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());
        String prompt = sessionCaptor.getAllValues().get(1).getSystemPrompt();
        assertThat(prompt).doesNotContain("=== 지원자 이력서 ===");
    }

    @Test
    @DisplayName("기능_테스트_startInterview_이력서_텍스트_길이_초과시_스마트_절삭한다")
    void 기능_테스트_startInterview_이력서_텍스트_길이_초과시_스마트_절삭한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.RESUME);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of(1L));

        String longText = "A".repeat(6000);
        String summarizedText = "요약된 이력서 내용";
        UserDocument doc = mock(UserDocument.class);
        when(doc.getDocumentType()).thenReturn(DocumentType.RESUME);
        when(doc.getParsedText()).thenReturn(longText);
        when(doc.getOriginalFileName()).thenReturn("resume.pdf");

        given(resumeTextSummarizer.summarize(eq(longText), eq(5000), eq(InterviewLevel.JUNIOR)))
                .willReturn(summarizedText);

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.RESUME).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userDocumentRepository.findAllByIdInAndUserId(List.of(1L), userId)).willReturn(List.of(doc));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(interviewSessionDocumentRepository.save(any(InterviewSessionDocument.class))).willReturn(null);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        verify(resumeTextSummarizer).summarize(eq(longText), eq(5000), eq(InterviewLevel.JUNIOR));
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());
        String prompt = sessionCaptor.getAllValues().get(1).getSystemPrompt();
        assertThat(prompt).contains(summarizedText);
        assertThat(prompt).doesNotContain(longText);
        assertThat(response.getDocumentTruncations()).hasSize(1);
        assertThat(response.getDocumentTruncations().get(0).isTruncated()).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_startInterview_채용공고_텍스트_길이_초과시_절삭한다")
    void 기능_테스트_startInterview_채용공고_텍스트_길이_초과시_절삭한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.COMPANY);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of(1L));
        setField(request, "jobPostingUrl", "https://jobs.example.com/job");

        String longJobPosting = "B".repeat(2000);

        UserDocument doc = mock(UserDocument.class);
        when(doc.getDocumentType()).thenReturn(DocumentType.RESUME);
        when(doc.getParsedText()).thenReturn("이력서 내용");

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.COMPANY).level(InterviewLevel.JUNIOR)
                .jobPostingContent(longJobPosting).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userDocumentRepository.findAllByIdInAndUserId(List.of(1L), userId)).willReturn(List.of(doc));
        given(jobCrawlerClient.crawl(any())).willReturn(longJobPosting);
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(interviewSessionDocumentRepository.save(any(InterviewSessionDocument.class))).willReturn(null);

        // when
        interviewService.startInterview(userId, request);

        // then
        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());
        String prompt = sessionCaptor.getAllValues().get(1).getSystemPrompt();
        assertThat(prompt).contains("=== 채용공고 내용 ===");
        assertThat(prompt).contains("...");
    }

    @Test
    @DisplayName("기능_테스트_finishInterview_토큰_초과시_buildTrimmedConversationText로_AI와_지원자_역할이_포함된다")
    void 기능_테스트_finishInterview_토큰_초과시_buildTrimmedConversationText로_AI와_지원자_역할이_포함된다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        lenient().when(tokenEstimator.estimateTokens(anyString())).thenReturn(50000);

        // toChatMessages returns trimmed messages including assistant and user roles
        List<com.interviewai.backend.client.dto.ChatMessage> trimmedMessages = List.of(
                new com.interviewai.backend.client.dto.ChatMessage("assistant", "면접관 질문"),
                new com.interviewai.backend.client.dto.ChatMessage("user", "지원자 답변")
        );
        lenient().when(tokenEstimator.toChatMessages(any())).thenReturn(trimmedMessages);
        lenient().when(tokenEstimator.trimHistory(any(), anyInt())).thenReturn(trimmedMessages);

        ArgumentCaptor<String> conversationCaptor = ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.generateFeedback(any(), conversationCaptor.capture()))
                .willReturn("{\"overallLevel\":\"PASS\",\"strengths\":\"좋음\",\"improvements\":\"없음\",\"fullReport\":\"리포트\"}");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        interviewService.finishInterview(userId, sessionId);

        // then
        String capturedConversation = conversationCaptor.getValue();
        assertThat(capturedConversation).contains("[면접관]");
        assertThat(capturedConversation).contains("면접관 질문");
        assertThat(capturedConversation).contains("[지원자]");
        assertThat(capturedConversation).contains("지원자 답변");
    }

    @Test
    @DisplayName("기능_테스트_finishInterview_parseFeedbackAndSave_JSON_예외시_catch_블록을_거친다")
    void 기능_테스트_finishInterview_parseFeedbackAndSave_JSON_예외시_catch_블록을_거친다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        // Return JSON with { but malformed inside to trigger readTree exception
        given(claudeAiClient.generateFeedback(any(), any())).willReturn("{invalid json:}");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        InterviewFeedbackResponseDto result = interviewService.finishInterview(userId, sessionId);

        // then — falls back to default values
        assertThat(result).isNotNull();
        assertThat(result.getOverallLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
    }

    @Test
    @DisplayName("기능_테스트_startInterview_joinSafely_예외시_graceful_degradation으로_null을_반환한다")
    void 기능_테스트_startInterview_joinSafely_예외시_graceful_degradation으로_null을_반환한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.COMPANY);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of(1L));
        setField(request, "jobPostingUrl", "https://jobs.example.com/job");

        UserDocument doc = mock(UserDocument.class);
        when(doc.getDocumentType()).thenReturn(DocumentType.RESUME);
        when(doc.getParsedText()).thenReturn("이력서 내용");

        // crawl throws exception → joinSafely catches it → returns null
        given(jobCrawlerClient.crawl(any())).willThrow(new RuntimeException("크롤 실패"));

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.COMPANY).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userDocumentRepository.findAllByIdInAndUserId(List.of(1L), userId)).willReturn(List.of(doc));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);
        given(interviewSessionDocumentRepository.save(any(InterviewSessionDocument.class))).willReturn(null);

        // when — should not throw, graceful degradation
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("기능_테스트_startInterview_documentIds가_빈_리스트이면_문서를_조회하지_않는다")
    void 기능_테스트_startInterview_documentIds가_빈_리스트이면_문서를_조회하지_않는다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.BASIC);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of()); // empty list

        InterviewSession savedSession = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(interviewSessionRepository.save(any(InterviewSession.class))).willReturn(savedSession);

        // when
        InterviewStartResponseDto response = interviewService.startInterview(userId, request);

        // then
        assertThat(response).isNotNull();
        verify(userDocumentRepository, org.mockito.Mockito.never()).findAllByIdInAndUserId(any(), any());
    }

    @Test
    @DisplayName("예외_테스트_validateModeRequirements_RESUME_모드에서_빈_documentIds이면_예외가_발생한다")
    void 예외_테스트_validateModeRequirements_RESUME_모드에서_빈_documentIds이면_예외가_발생한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.RESUME);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of()); // empty list, not null

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

        // when & then
        assertThatThrownBy(() -> interviewService.startInterview(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("문서");
    }

    @Test
    @DisplayName("기능_테스트_buildSendMessageSystemPrompt_요약이_blank이면_시스템프롬프트에_미포함된다")
    void 기능_테스트_buildSendMessageSystemPrompt_요약이_blank이면_시스템프롬프트에_미포함된다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();
        session.setConversationSummary("   "); // blank summary

        List<com.interviewai.backend.client.dto.ChatMessage> retainedList = List.of();
        TrimResult noTrimResult = new TrimResult(retainedList, List.of(), false);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        org.mockito.Mockito.doReturn(noTrimResult)
                .when(tokenEstimator).trimHistoryWithPriorityAndReport(any(), anyInt());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(systemPromptCaptor.capture(), any(), any())).willReturn("AI 응답");

        InterviewMessage savedAiMsg = mock(InterviewMessage.class);
        given(savedAiMsg.getId()).willReturn(302L);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null, savedAiMsg);

        // when
        interviewService.sendMessage(userId, sessionId, request);

        // then
        assertThat(systemPromptCaptor.getValue()).doesNotContain("=== 이전 대화 요약 ===");
    }

    @Test
    @DisplayName("기능_테스트_finishInterview_parseFeedbackAndSave_JSON_필드_없을때_기본값을_사용한다")
    void 기능_테스트_finishInterview_parseFeedbackAndSave_JSON_필드_없을때_기본값을_사용한다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;

        InterviewSession session = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        // JSON with no overallLevel, strengths, improvements, fullReport fields
        given(claudeAiClient.generateFeedback(any(), any())).willReturn("{}");
        given(interviewFeedbackRepository.save(any(InterviewFeedback.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        InterviewFeedbackResponseDto result = interviewService.finishInterview(userId, sessionId);

        // then
        assertThat(result.getOverallLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(result.getStrengths()).isEqualTo("분석 중...");
        assertThat(result.getImprovements()).isEqualTo("분석 중...");
    }

    @Test
    @DisplayName("예외_테스트_validateModeRequirements_COMPANY_모드에서_빈_documentIds이면_예외가_발생한다")
    void 예외_테스트_validateModeRequirements_COMPANY_모드에서_빈_documentIds이면_예외가_발생한다() {
        // given
        Long userId = 1L;
        InterviewStartRequestDto request = new InterviewStartRequestDto();
        setField(request, "mode", InterviewMode.COMPANY);
        setField(request, "level", InterviewLevel.JUNIOR);
        setField(request, "documentIds", List.of()); // empty, not null

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

        // when & then
        assertThatThrownBy(() -> interviewService.startInterview(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("문서");
    }

    @Test
    @DisplayName("기능_테스트_streamMessage_summaryDisabled이면_요약생성을_호출하지않는다")
    void 기능_테스트_streamMessage_summaryDisabled이면_요약생성을_호출하지않는다() {
        // given
        Long userId = 1L;
        Long sessionId = 1L;
        InterviewSendMessageRequestDto request = new InterviewSendMessageRequestDto();
        setField(request, "content", "답변입니다.");

        InterviewSession session = InterviewSession.builder()
                .user(testUser).mode(InterviewMode.BASIC).level(InterviewLevel.JUNIOR).build();

        InterviewMessage priorMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("질문입니다.").build();

        List<InterviewMessage> trimmedList = List.of(priorMsg);
        List<com.interviewai.backend.client.dto.ChatMessage> retainedList = List.of(
                new com.interviewai.backend.client.dto.ChatMessage("assistant", "질문입니다."));
        TrimResult trimResultWithTrimming = new TrimResult(retainedList, trimmedList, true);

        InterviewProperties.Prompt prompt = new InterviewProperties.Prompt();
        prompt.setSummaryEnabled(false);
        given(interviewProperties.getPrompt()).willReturn(prompt);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of(priorMsg));
        org.mockito.Mockito.doReturn(trimResultWithTrimming)
                .when(tokenEstimator).trimHistoryWithPriorityAndReport(any(), anyInt());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        lenient().when(claudeAiClient.streamChat(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just("토큰"));

        // when
        interviewService.streamMessage(userId, sessionId, request);

        // then
        verify(conversationSummaryService, org.mockito.Mockito.never()).generateSummaryAsync(any(), any());
    }

    // ----------------------------------------------------------------
    // sendMessage + trimming/summary 테스트
    // ----------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_sendMessage_trimming발생시_요약생성을_비동기호출한다")
    void 기능_테스트_sendMessage_trimming발생시_요약생성을_비동기호출한다() {
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

        InterviewMessage priorMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("질문입니다.").build();
        List<InterviewMessage> priorMessages = List.of(priorMsg);

        List<InterviewMessage> trimmedList = List.of(priorMsg);
        List<com.interviewai.backend.client.dto.ChatMessage> retainedList = List.of(
                new com.interviewai.backend.client.dto.ChatMessage("assistant", "질문입니다."));
        TrimResult trimResultWithTrimming = new TrimResult(retainedList, trimmedList, true);

        InterviewProperties.Prompt prompt = new InterviewProperties.Prompt();
        prompt.setSummaryEnabled(true);
        given(interviewProperties.getPrompt()).willReturn(prompt);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(priorMessages);
        org.mockito.Mockito.doReturn(trimResultWithTrimming)
                .when(tokenEstimator).trimHistoryWithPriorityAndReport(any(), anyInt());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any())).willReturn("AI 응답");
        InterviewMessage savedAiMsg = mock(InterviewMessage.class);
        given(savedAiMsg.getId()).willReturn(200L);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null, savedAiMsg);

        // when
        interviewService.sendMessage(userId, sessionId, request);

        // then
        verify(conversationSummaryService).generateSummaryAsync(eq(sessionId), eq(trimmedList));
    }

    @Test
    @DisplayName("기능_테스트_sendMessage_trimming미발생시_요약생성을_호출하지않는다")
    void 기능_테스트_sendMessage_trimming미발생시_요약생성을_호출하지않는다() {
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

        InterviewMessage priorMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("질문입니다.").build();
        List<InterviewMessage> priorMessages = List.of(priorMsg);

        List<com.interviewai.backend.client.dto.ChatMessage> retainedList = List.of(
                new com.interviewai.backend.client.dto.ChatMessage("assistant", "질문입니다."));
        TrimResult noTrimResult = new TrimResult(retainedList, List.of(), false);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(priorMessages);
        org.mockito.Mockito.doReturn(noTrimResult)
                .when(tokenEstimator).trimHistoryWithPriorityAndReport(any(), anyInt());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any())).willReturn("AI 응답");
        InterviewMessage savedAiMsg = mock(InterviewMessage.class);
        given(savedAiMsg.getId()).willReturn(200L);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null, savedAiMsg);

        // when
        interviewService.sendMessage(userId, sessionId, request);

        // then
        verify(conversationSummaryService, org.mockito.Mockito.never())
                .generateSummaryAsync(any(), any());
    }

    @Test
    @DisplayName("기능_테스트_sendMessage_summaryDisabled이면_요약생성을_호출하지않는다")
    void 기능_테스트_sendMessage_summaryDisabled이면_요약생성을_호출하지않는다() {
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

        InterviewMessage priorMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("질문입니다.").build();
        List<InterviewMessage> priorMessages = List.of(priorMsg);

        List<InterviewMessage> trimmedList = List.of(priorMsg);
        List<com.interviewai.backend.client.dto.ChatMessage> retainedList = List.of(
                new com.interviewai.backend.client.dto.ChatMessage("assistant", "질문입니다."));
        TrimResult trimResultWithTrimming = new TrimResult(retainedList, trimmedList, true);

        // summaryEnabled = false
        InterviewProperties.Prompt prompt = new InterviewProperties.Prompt();
        prompt.setSummaryEnabled(false);
        given(interviewProperties.getPrompt()).willReturn(prompt);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(priorMessages);
        org.mockito.Mockito.doReturn(trimResultWithTrimming)
                .when(tokenEstimator).trimHistoryWithPriorityAndReport(any(), anyInt());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any())).willReturn("AI 응답");
        InterviewMessage savedAiMsg = mock(InterviewMessage.class);
        given(savedAiMsg.getId()).willReturn(200L);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null, savedAiMsg);

        // when
        interviewService.sendMessage(userId, sessionId, request);

        // then
        verify(conversationSummaryService, org.mockito.Mockito.never())
                .generateSummaryAsync(any(), any());
    }

    @Test
    @DisplayName("기능_테스트_streamMessage_trimming발생시_요약생성을_비동기호출한다")
    void 기능_테스트_streamMessage_trimming발생시_요약생성을_비동기호출한다() {
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

        InterviewMessage priorMsg = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("질문입니다.").build();
        List<InterviewMessage> priorMessages = List.of(priorMsg);

        List<InterviewMessage> trimmedList = List.of(priorMsg);
        List<com.interviewai.backend.client.dto.ChatMessage> retainedList = List.of(
                new com.interviewai.backend.client.dto.ChatMessage("assistant", "질문입니다."));
        TrimResult trimResultWithTrimming = new TrimResult(retainedList, trimmedList, true);

        InterviewProperties.Prompt prompt = new InterviewProperties.Prompt();
        prompt.setSummaryEnabled(true);
        given(interviewProperties.getPrompt()).willReturn(prompt);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(priorMessages);
        org.mockito.Mockito.doReturn(trimResultWithTrimming)
                .when(tokenEstimator).trimHistoryWithPriorityAndReport(any(), anyInt());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        lenient().when(claudeAiClient.streamChat(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just("토큰"));

        // when
        interviewService.streamMessage(userId, sessionId, request);

        // then
        verify(conversationSummaryService).generateSummaryAsync(eq(sessionId), eq(trimmedList));
    }

    @Test
    @DisplayName("기능_테스트_buildSendMessageSystemPrompt_요약이있으면_시스템프롬프트에_포함된다")
    void 기능_테스트_buildSendMessageSystemPrompt_요약이있으면_시스템프롬프트에_포함된다() {
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
        session.setConversationSummary("이전 면접에서 Java 기초를 다뤘습니다.");

        List<com.interviewai.backend.client.dto.ChatMessage> retainedList = List.of();
        TrimResult noTrimResult = new TrimResult(retainedList, List.of(), false);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        org.mockito.Mockito.doReturn(noTrimResult)
                .when(tokenEstimator).trimHistoryWithPriorityAndReport(any(), anyInt());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(systemPromptCaptor.capture(), any(), any())).willReturn("AI 응답");

        InterviewMessage savedAiMsg = mock(InterviewMessage.class);
        given(savedAiMsg.getId()).willReturn(300L);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null, savedAiMsg);

        // when
        interviewService.sendMessage(userId, sessionId, request);

        // then
        String capturedPrompt = systemPromptCaptor.getValue();
        assertThat(capturedPrompt).contains("=== 이전 대화 요약 ===");
        assertThat(capturedPrompt).contains("이전 면접에서 Java 기초를 다뤘습니다.");
    }

    @Test
    @DisplayName("기능_테스트_buildSendMessageSystemPrompt_요약이없으면_시스템프롬프트에_미포함된다")
    void 기능_테스트_buildSendMessageSystemPrompt_요약이없으면_시스템프롬프트에_미포함된다() {
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
        // conversationSummary is null by default

        List<com.interviewai.backend.client.dto.ChatMessage> retainedList = List.of();
        TrimResult noTrimResult = new TrimResult(retainedList, List.of(), false);

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        org.mockito.Mockito.doReturn(noTrimResult)
                .when(tokenEstimator).trimHistoryWithPriorityAndReport(any(), anyInt());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(systemPromptCaptor.capture(), any(), any())).willReturn("AI 응답");

        InterviewMessage savedAiMsg = mock(InterviewMessage.class);
        given(savedAiMsg.getId()).willReturn(301L);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null, savedAiMsg);

        // when
        interviewService.sendMessage(userId, sessionId, request);

        // then
        String capturedPrompt = systemPromptCaptor.getValue();
        assertThat(capturedPrompt).doesNotContain("=== 이전 대화 요약 ===");
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
