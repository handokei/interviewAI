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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
    @DisplayName("기능_테스트_인터뷰_통계를_조회한다")
    void 기능_테스트_인터뷰_통계를_조회한다() {
        // given
        Long userId = 1L;

        given(interviewSessionRepository.countByUserId(userId)).willReturn(10L);
        given(interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.COMPLETED)).willReturn(7L);
        given(interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.CANCELLED)).willReturn(2L);
        given(interviewFeedbackRepository.findAverageScoreByUserId(userId)).willReturn(78.5);

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
        assertThat(result.getAverageScore()).isEqualTo(78.5);
        assertThat(result.getModeDistribution()).containsEntry("BASIC", 5L);
        assertThat(result.getModeDistribution()).containsEntry("RESUME", 3L);
        assertThat(result.getModeDistribution()).containsEntry("COMPANY", 2L);
        assertThat(result.getLevelDistribution()).containsEntry("JUNIOR", 6L);
        assertThat(result.getLevelDistribution()).containsEntry("SENIOR", 4L);
    }

    @Test
    @DisplayName("기능_테스트_피드백이_없을때_평균점수가_null이다")
    void 기능_테스트_피드백이_없을때_평균점수가_null이다() {
        // given
        Long userId = 1L;

        given(interviewSessionRepository.countByUserId(userId)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.COMPLETED)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.CANCELLED)).willReturn(0L);
        given(interviewFeedbackRepository.findAverageScoreByUserId(userId)).willReturn(null);

        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.BASIC)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.RESUME)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndMode(userId, InterviewMode.COMPANY)).willReturn(0L);

        given(interviewSessionRepository.countByUserIdAndLevel(userId, InterviewLevel.JUNIOR)).willReturn(0L);
        given(interviewSessionRepository.countByUserIdAndLevel(userId, InterviewLevel.SENIOR)).willReturn(0L);

        // when
        InterviewStatsResponseDto result = interviewService.getMyStats(userId);

        // then
        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getAverageScore()).isNull();
    }

    @Test
    @DisplayName("기능_테스트_메시지_전송_시_suggestFinish_가_false_로_반환된다")
    void 기능_테스트_메시지_전송_시_suggestFinish_가_false_로_반환된다() {
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

        String structuredResponse = "{\"nextQuestion\": \"다음 질문입니다.\", \"suggestFinish\": false}";

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any())).willReturn(structuredResponse);

        // when
        InterviewSendMessageResponseDto response = interviewService.sendMessage(userId, sessionId, request);

        // then
        assertThat(response.getAiResponse()).isEqualTo("다음 질문입니다.");
        assertThat(response.isSuggestFinish()).isFalse();
    }

    @Test
    @DisplayName("기능_테스트_AI가_충분성_신호를_보내면_suggestFinish_가_true_로_반환된다")
    void 기능_테스트_AI가_충분성_신호를_보내면_suggestFinish_가_true_로_반환된다() {
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

        String structuredResponse = "{\"nextQuestion\": \"수고하셨습니다. 추가로 하실 말씀이 있으신가요?\", \"suggestFinish\": true}";

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any())).willReturn(structuredResponse);

        // when
        InterviewSendMessageResponseDto response = interviewService.sendMessage(userId, sessionId, request);

        // then
        assertThat(response.getAiResponse()).isEqualTo("수고하셨습니다. 추가로 하실 말씀이 있으신가요?");
        assertThat(response.isSuggestFinish()).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_AI_응답_파싱_실패시_원본_텍스트를_nextQuestion으로_사용한다")
    void 기능_테스트_AI_응답_파싱_실패시_원본_텍스트를_nextQuestion으로_사용한다() {
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

        String plainTextResponse = "JSON이 아닌 일반 텍스트 응답입니다.";

        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(List.of());
        given(interviewSessionDocumentRepository.findBySessionId(sessionId)).willReturn(List.of());
        given(claudeAiClient.chat(any(), any(), any())).willReturn(plainTextResponse);

        // when
        InterviewSendMessageResponseDto response = interviewService.sendMessage(userId, sessionId, request);

        // then
        assertThat(response.getAiResponse()).isEqualTo(plainTextResponse);
        assertThat(response.isSuggestFinish()).isFalse();
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
