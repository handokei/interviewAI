package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.ClaudeAiClient;
import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewSessionRepository;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    @InjectMocks
    private ConversationSummaryService conversationSummaryService;

    @Mock
    private ClaudeAiClient claudeAiClient;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private TokenEstimator tokenEstimator;

    @Mock
    private InterviewProperties interviewProperties;

    private User testUser;
    private InterviewSession testSession;

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

        testSession = InterviewSession.builder()
                .user(testUser)
                .mode(com.interviewai.backend.interview.enums.InterviewMode.BASIC)
                .level(com.interviewai.backend.interview.enums.InterviewLevel.JUNIOR)
                .build();

        InterviewProperties.Prompt prompt = new InterviewProperties.Prompt();
        lenient().when(interviewProperties.getPrompt()).thenReturn(prompt);
    }

    // ----------------------------------------------------------------
    // generateSummaryAsync 테스트
    // ----------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_generateSummaryAsync_trimmedMessages가_있으면_요약을_생성하고_세션에_저장한다")
    void 기능_테스트_generateSummaryAsync_trimmedMessages가_있으면_요약을_생성하고_세션에_저장한다() {
        // given
        Long sessionId = 1L;
        InterviewMessage msg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("Java의 특징을 설명해주세요.")
                .build();

        given(interviewSessionRepository.findById(sessionId)).willReturn(Optional.of(testSession));
        given(claudeAiClient.chat(any(), any(), any())).willReturn("요약 내용입니다.");

        // when
        conversationSummaryService.generateSummaryAsync(sessionId, List.of(msg));

        // then
        assertThat(testSession.getConversationSummary()).isEqualTo("요약 내용입니다.");
        verify(claudeAiClient).chat(any(), any(), any());
    }

    @Test
    @DisplayName("기능_테스트_generateSummaryAsync_기존요약이_있으면_누적하여_갱신한다")
    void 기능_테스트_generateSummaryAsync_기존요약이_있으면_누적하여_갱신한다() {
        // given
        Long sessionId = 1L;
        testSession.setConversationSummary("기존 요약입니다.");

        InterviewMessage msg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.AI)
                .content("새로운 질문입니다.")
                .build();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        given(interviewSessionRepository.findById(sessionId)).willReturn(Optional.of(testSession));
        given(claudeAiClient.chat(promptCaptor.capture(), any(), any())).willReturn("누적 요약입니다.");

        // when
        conversationSummaryService.generateSummaryAsync(sessionId, List.of(msg));

        // then
        assertThat(testSession.getConversationSummary()).isEqualTo("누적 요약입니다.");
        assertThat(promptCaptor.getValue()).contains("기존 요약입니다.");
        assertThat(promptCaptor.getValue()).contains("위 기존 요약에 아래 새로운 대화 내용을 통합하여 누적 요약을 작성해주세요.");
    }

    @Test
    @DisplayName("기능_테스트_generateSummaryAsync_AI호출_실패시_로그만_남기고_종료한다")
    void 기능_테스트_generateSummaryAsync_AI호출_실패시_로그만_남기고_종료한다() {
        // given
        Long sessionId = 1L;
        InterviewMessage msg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("답변입니다.")
                .build();

        given(interviewSessionRepository.findById(sessionId)).willReturn(Optional.of(testSession));
        given(claudeAiClient.chat(any(), any(), any())).willThrow(new RuntimeException("API 호출 실패"));

        // when — 예외 없이 종료되어야 함
        conversationSummaryService.generateSummaryAsync(sessionId, List.of(msg));

        // then — conversationSummary가 설정되지 않았음을 확인
        assertThat(testSession.getConversationSummary()).isNull();
    }

    @Test
    @DisplayName("기능_테스트_generateSummaryAsync_세션이_없으면_로그만_남기고_종료한다")
    void 기능_테스트_generateSummaryAsync_세션이_없으면_로그만_남기고_종료한다() {
        // given
        Long sessionId = 999L;
        InterviewMessage msg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("답변입니다.")
                .build();

        given(interviewSessionRepository.findById(sessionId)).willReturn(Optional.empty());

        // when — 예외 없이 종료되어야 함
        conversationSummaryService.generateSummaryAsync(sessionId, List.of(msg));

        // then
        verify(claudeAiClient, never()).chat(any(), any(), any());
    }

    @Test
    @DisplayName("기능_테스트_generateSummaryAsync_빈_trimmedMessages면_스킵한다")
    void 기능_테스트_generateSummaryAsync_빈_trimmedMessages면_스킵한다() {
        // given
        Long sessionId = 1L;

        // when
        conversationSummaryService.generateSummaryAsync(sessionId, List.of());

        // then
        verify(interviewSessionRepository, never()).findById(any());
        verify(claudeAiClient, never()).chat(any(), any(), any());
    }

    @Test
    @DisplayName("기능_테스트_generateSummaryAsync_null_trimmedMessages면_스킵한다")
    void 기능_테스트_generateSummaryAsync_null_trimmedMessages면_스킵한다() {
        // given
        Long sessionId = 1L;

        // when
        conversationSummaryService.generateSummaryAsync(sessionId, null);

        // then
        verify(interviewSessionRepository, never()).findById(any());
        verify(claudeAiClient, never()).chat(any(), any(), any());
    }

    // ----------------------------------------------------------------
    // generateSummary 테스트
    // ----------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_generateSummary_maxSummaryTokens_초과시_절삭한다")
    void 기능_테스트_generateSummary_maxSummaryTokens_초과시_절삭한다() {
        // given
        InterviewProperties.Prompt prompt = new InterviewProperties.Prompt();
        prompt.setMaxSummaryTokens(2);  // maxSummaryTokens=2, charsPerToken=3 → maxChars=6
        given(interviewProperties.getPrompt()).willReturn(prompt);

        InterviewMessage msg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("답변입니다.")
                .build();

        // AI가 6자 초과 응답을 반환
        given(claudeAiClient.chat(any(), any(), any())).willReturn("1234567890");

        // when
        String result = conversationSummaryService.generateSummary(null, List.of(msg));

        // then — maxChars(6)로 절삭되어야 함
        assertThat(result).hasSize(6);
        assertThat(result).isEqualTo("123456");
    }

    @Test
    @DisplayName("기능_테스트_generateSummary_maxSummaryTokens_미초과시_절삭하지_않는다")
    void 기능_테스트_generateSummary_maxSummaryTokens_미초과시_절삭하지_않는다() {
        // given
        // default: maxSummaryTokens=500, charsPerToken=3 → maxChars=1500
        InterviewMessage msg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("답변입니다.")
                .build();

        given(claudeAiClient.chat(any(), any(), any())).willReturn("짧은 요약");

        // when
        String result = conversationSummaryService.generateSummary(null, List.of(msg));

        // then
        assertThat(result).isEqualTo("짧은 요약");
    }

    // ----------------------------------------------------------------
    // buildSummaryPrompt 테스트
    // ----------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_buildSummaryPrompt_기존요약이_없으면_새로운_대화만_포함한다")
    void 기능_테스트_buildSummaryPrompt_기존요약이_없으면_새로운_대화만_포함한다() {
        // given
        InterviewMessage aiMsg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.AI)
                .content("Java란 무엇인가요?")
                .build();
        InterviewMessage userMsg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("Java는 객체지향 언어입니다.")
                .build();

        // when
        String prompt = conversationSummaryService.buildSummaryPrompt(null, List.of(aiMsg, userMsg));

        // then
        assertThat(prompt).contains("=== 새로운 대화 내용 ===");
        assertThat(prompt).contains("[면접관]");
        assertThat(prompt).contains("Java란 무엇인가요?");
        assertThat(prompt).contains("[지원자]");
        assertThat(prompt).contains("Java는 객체지향 언어입니다.");
        assertThat(prompt).doesNotContain("=== 기존 요약 ===");
        assertThat(prompt).doesNotContain("위 기존 요약에 아래 새로운 대화 내용을 통합하여");
    }

    @Test
    @DisplayName("기능_테스트_buildSummaryPrompt_기존요약이_있으면_누적지시가_포함된다")
    void 기능_테스트_buildSummaryPrompt_기존요약이_있으면_누적지시가_포함된다() {
        // given
        String existingSummary = "1차 면접 요약: Java 기초 질문을 다뤘습니다.";
        InterviewMessage userMsg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("Spring Boot는 무엇인가요?")
                .build();

        // when
        String prompt = conversationSummaryService.buildSummaryPrompt(existingSummary, List.of(userMsg));

        // then
        assertThat(prompt).contains("=== 기존 요약 ===");
        assertThat(prompt).contains(existingSummary);
        assertThat(prompt).contains("위 기존 요약에 아래 새로운 대화 내용을 통합하여 누적 요약을 작성해주세요.");
        assertThat(prompt).contains("=== 새로운 대화 내용 ===");
    }

    @Test
    @DisplayName("기능_테스트_buildSummaryPrompt_기존요약이_blank이면_누적지시가_포함되지_않는다")
    void 기능_테스트_buildSummaryPrompt_기존요약이_blank이면_누적지시가_포함되지_않는다() {
        // given
        InterviewMessage userMsg = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("답변입니다.")
                .build();

        // when — blank 문자열 전달
        String prompt = conversationSummaryService.buildSummaryPrompt("   ", List.of(userMsg));

        // then
        assertThat(prompt).doesNotContain("=== 기존 요약 ===");
        assertThat(prompt).doesNotContain("위 기존 요약에 아래 새로운 대화 내용을 통합하여");
    }
}
