package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.ClaudeAiClient;
import com.interviewai.backend.client.dto.ChatMessage;
import com.interviewai.backend.interview.enums.AnswerLevel;
import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
import com.interviewai.backend.llm.service.LlmJsonSanitizer;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import com.interviewai.backend.global.config.InterviewProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InterviewEvaluationServiceTest {

    @InjectMocks
    private InterviewEvaluationService interviewEvaluationService;

    @Mock
    private InterviewMessageRepository interviewMessageRepository;

    @Mock
    private ClaudeAiClient claudeAiClient;

    @Mock
    private TokenEstimator tokenEstimator;

    @Mock
    private InterviewProperties interviewProperties;

    @Spy
    private LlmJsonSanitizer llmJsonSanitizer = new LlmJsonSanitizer();

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
        lenient().when(interviewProperties.getPrompt()).thenReturn(prompt);
        lenient().when(tokenEstimator.trimHistory(any(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
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
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then
        assertThat(eval.suggestFinish()).isTrue();
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(eval.qualityHint()).isEqualTo("정확한 답변이었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_잘못된_answerLevel_값이면_NEEDS_IMPROVEMENT_fallback으로_반환된다")
    void 기능_테스트_잘못된_answerLevel_값이면_NEEDS_IMPROVEMENT_fallback으로_반환된다() {
        // given
        String evalJson = "{\"suggestFinish\":false,\"answerLevel\":\"INVALID_LEVEL\",\"qualityHint\":\"힌트\"}";

        // when
        InterviewEvaluation eval = interviewEvaluationService.parseEvaluation(evalJson);

        // then
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(eval.qualityHint()).isEqualTo("힌트");
    }

    @Test
    @DisplayName("기능_테스트_JSON_파싱_실패시_fallback_값으로_반환된다")
    void 기능_테스트_JSON_파싱_실패시_fallback_값으로_반환된다() {
        // when
        InterviewEvaluation eval = interviewEvaluationService.parseEvaluation("invalid json response");

        // then
        assertThat(eval.suggestFinish()).isFalse();
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(eval.qualityHint()).isEqualTo("답변이 접수되었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_evaluateAsync_정상_평가_결과를_DB에_저장한다")
    void 기능_테스트_evaluateAsync_정상_평가_결과를_DB에_저장한다() {
        // given
        Long aiMessageId = 100L;
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        InterviewMessage aiMessage = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("AI 응답입니다.").build();
        ReflectionTestUtils.setField(aiMessage, "id", aiMessageId);

        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("{\"suggestFinish\":true,\"answerLevel\":\"PASS\",\"qualityHint\":\"훌륭합니다.\"}");
        given(interviewMessageRepository.findById(aiMessageId)).willReturn(Optional.of(aiMessage));

        // when
        interviewEvaluationService.evaluateAsync(aiMessageId, session, List.of(), "AI 응답입니다.");

        // then
        assertThat(aiMessage.getAnswerLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(aiMessage.getSuggestFinish()).isTrue();
        assertThat(aiMessage.getQualityHint()).isEqualTo("훌륭합니다.");
    }

    @Test
    @DisplayName("기능_테스트_evaluateAsync_AI_호출_실패시_fallback_평가를_저장한다")
    void 기능_테스트_evaluateAsync_AI_호출_실패시_fallback_평가를_저장한다() {
        // given
        Long aiMessageId = 100L;
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        InterviewMessage aiMessage = InterviewMessage.builder()
                .session(session).role(MessageRole.AI).content("AI 응답입니다.").build();
        ReflectionTestUtils.setField(aiMessage, "id", aiMessageId);

        given(claudeAiClient.chat(any(), any(), any()))
                .willThrow(new RuntimeException("API 호출 실패"));
        given(interviewMessageRepository.findById(aiMessageId)).willReturn(Optional.of(aiMessage));

        // when
        interviewEvaluationService.evaluateAsync(aiMessageId, session, List.of(), "AI 응답입니다.");

        // then
        assertThat(aiMessage.getAnswerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(aiMessage.getSuggestFinish()).isFalse();
        assertThat(aiMessage.getQualityHint()).isEqualTo("답변이 접수되었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_SENIOR_레벨_프롬프트에_경력_개발자_설명이_포함된다")
    void 기능_테스트_SENIOR_레벨_프롬프트에_경력_개발자_설명이_포함된다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.SENIOR)
                .build();

        org.mockito.ArgumentCaptor<String> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(promptCaptor.capture(), any(), any()))
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"PASS\",\"qualityHint\":\"좋습니다.\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then
        assertThat(promptCaptor.getValue()).contains("경력 개발자");
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
    }

    @Test
    @DisplayName("기능_테스트_jobTitle이_null이면_프롬프트에_지원_직무_줄이_포함되지_않는다")
    void 기능_테스트_jobTitle이_null이면_프롬프트에_지원_직무_줄이_포함되지_않는다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        // jobTitle 설정 안 함 — null

        org.mockito.ArgumentCaptor<String> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(promptCaptor.capture(), any(), any()))
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"NEEDS_IMPROVEMENT\",\"qualityHint\":\"힌트\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then
        assertThat(promptCaptor.getValue()).doesNotContain("지원 직무:");
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
    }

    @Test
    @DisplayName("기능_테스트_jobTitle이_있으면_프롬프트에_지원_직무_줄이_포함된다")
    void 기능_테스트_jobTitle이_있으면_프롬프트에_지원_직무_줄이_포함된다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .jobTitle("백엔드 개발자")
                .build();

        org.mockito.ArgumentCaptor<String> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(promptCaptor.capture(), any(), any()))
                .willReturn("{\"suggestFinish\":true,\"answerLevel\":\"PASS\",\"qualityHint\":\"훌륭합니다.\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then
        assertThat(promptCaptor.getValue()).contains("지원 직무: 백엔드 개발자");
        assertThat(eval.suggestFinish()).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_parseEvaluation_suggestFinish_필드_없는_JSON이면_false로_반환된다")
    void 기능_테스트_parseEvaluation_suggestFinish_필드_없는_JSON이면_false로_반환된다() {
        // given
        String json = "{\"answerLevel\":\"PASS\",\"qualityHint\":\"좋습니다.\"}";

        // when
        InterviewEvaluation eval = interviewEvaluationService.parseEvaluation(json);

        // then
        assertThat(eval.suggestFinish()).isFalse();
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(eval.qualityHint()).isEqualTo("좋습니다.");
    }

    @Test
    @DisplayName("기능_테스트_parseEvaluation_answerLevel_필드_없는_JSON이면_NEEDS_IMPROVEMENT를_반환한다")
    void 기능_테스트_parseEvaluation_answerLevel_필드_없는_JSON이면_NEEDS_IMPROVEMENT를_반환한다() {
        // given
        String json = "{\"suggestFinish\":true,\"qualityHint\":\"힌트\"}";

        // when
        InterviewEvaluation eval = interviewEvaluationService.parseEvaluation(json);

        // then
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(eval.suggestFinish()).isTrue();
        assertThat(eval.qualityHint()).isEqualTo("힌트");
    }

    @Test
    @DisplayName("기능_테스트_parseEvaluation_qualityHint_필드_없는_JSON이면_기본_메시지를_반환한다")
    void 기능_테스트_parseEvaluation_qualityHint_필드_없는_JSON이면_기본_메시지를_반환한다() {
        // given
        String json = "{\"suggestFinish\":false,\"answerLevel\":\"PASS\"}";

        // when
        InterviewEvaluation eval = interviewEvaluationService.parseEvaluation(json);

        // then
        assertThat(eval.qualityHint()).isEqualTo("답변이 접수되었습니다.");
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
    }

    @Test
    @DisplayName("기능_테스트_parseEvaluation_중괄호가_없는_문자열이면_fallback을_반환한다")
    void 기능_테스트_parseEvaluation_중괄호가_없는_문자열이면_fallback을_반환한다() {
        // given
        String json = "suggestFinish:true,answerLevel:PASS";

        // when
        InterviewEvaluation eval = interviewEvaluationService.parseEvaluation(json);

        // then
        assertThat(eval.suggestFinish()).isFalse();
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(eval.qualityHint()).isEqualTo("답변이 접수되었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_evaluateAsync_메시지_미발견시_예외_없이_종료된다")
    void 기능_테스트_evaluateAsync_메시지_미발견시_예외_없이_종료된다() {
        // given
        Long aiMessageId = 999L;
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(claudeAiClient.chat(any(), any(), any()))
                .willThrow(new RuntimeException("API 호출 실패"));
        given(interviewMessageRepository.findById(aiMessageId)).willReturn(Optional.empty());

        // when — 예외 없이 종료되어야 함
        interviewEvaluationService.evaluateAsync(aiMessageId, session, List.of(), "AI 응답");
    }

    @Test
    @DisplayName("기능_테스트_buildEvalPrompt_history가_있으면_대화_내용이_프롬프트에_포함된다")
    void 기능_테스트_buildEvalPrompt_history가_있으면_대화_내용이_프롬프트에_포함된다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        List<ChatMessage> history = List.of(
                new ChatMessage("assistant", "면접관 질문입니다."),
                new ChatMessage("user", "지원자 답변입니다.")
        );

        org.mockito.ArgumentCaptor<String> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(promptCaptor.capture(), any(), any()))
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"PASS\",\"qualityHint\":\"좋습니다.\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, history, "AI 응답");

        // then
        assertThat(promptCaptor.getValue()).contains("[면접관]");
        assertThat(promptCaptor.getValue()).contains("면접관 질문입니다.");
        assertThat(promptCaptor.getValue()).contains("[지원자]");
        assertThat(promptCaptor.getValue()).contains("지원자 답변입니다.");
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
    }

    @Test
    @DisplayName("기능_테스트_parseEvaluation_readTree_예외시_catch_블록을_거쳐_fallback을_반환한다")
    void 기능_테스트_parseEvaluation_readTree_예외시_catch_블록을_거쳐_fallback을_반환한다() {
        // given — JSON with { and } but content causes readTree to fail
        String malformedJson = "{this is not valid json at all}";

        // when
        InterviewEvaluation eval = interviewEvaluationService.parseEvaluation(malformedJson);

        // then
        assertThat(eval.suggestFinish()).isFalse();
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(eval.qualityHint()).isEqualTo("답변이 접수되었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_buildEvalPrompt_요약이있으면_프롬프트에_포함된다")
    void 기능_테스트_buildEvalPrompt_요약이있으면_프롬프트에_포함된다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        session.setConversationSummary("이전 면접에서 Java 기초를 다뤘습니다.");

        org.mockito.ArgumentCaptor<String> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(promptCaptor.capture(), any(), any()))
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"PASS\",\"qualityHint\":\"좋습니다.\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then
        assertThat(promptCaptor.getValue()).contains("=== 이전 대화 요약 (참고용) ===");
        assertThat(promptCaptor.getValue()).contains("이전 면접에서 Java 기초를 다뤘습니다.");
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
    }

    @Test
    @DisplayName("기능_테스트_buildEvalPrompt_요약이없으면_프롬프트에_미포함된다")
    void 기능_테스트_buildEvalPrompt_요약이없으면_프롬프트에_미포함된다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
        // conversationSummary is null by default

        org.mockito.ArgumentCaptor<String> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        given(claudeAiClient.chat(promptCaptor.capture(), any(), any()))
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"NEEDS_IMPROVEMENT\",\"qualityHint\":\"힌트\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then
        assertThat(promptCaptor.getValue()).doesNotContain("=== 이전 대화 요약 (참고용) ===");
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
    }

    @Test
    @DisplayName("기능_테스트_evaluateAsync_AI_성공_후_메시지가_사라지면_catch를_거쳐_fallback이_적용된다")
    void 기능_테스트_evaluateAsync_AI_성공_후_메시지가_사라지면_catch를_거쳐_fallback이_적용된다() {
        // given
        Long aiMessageId = 200L;
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        // AI 평가 성공 후 findById가 empty 반환 → orElseThrow 람다 실행 → catch 블록으로 이동
        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("{\"suggestFinish\":true,\"answerLevel\":\"PASS\",\"qualityHint\":\"좋습니다.\"}");
        given(interviewMessageRepository.findById(aiMessageId))
                .willReturn(Optional.empty());  // 두 번 모두 empty

        // when — catch 블록에서 ifPresent가 호출되나 empty이므로 아무 일도 없음
        interviewEvaluationService.evaluateAsync(aiMessageId, session, List.of(), "AI 응답");

        // then — 예외 없이 종료되었는지 확인
        org.mockito.Mockito.verify(interviewMessageRepository,
                org.mockito.Mockito.times(2)).findById(aiMessageId);
    }

    @Test
    @DisplayName("기능_테스트_evaluateWithAi_1차_파싱_성공시_재호출_없이_1회만_호출한다")
    void 기능_테스트_evaluateWithAi_1차_파싱_성공시_재호출_없이_1회만_호출한다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("{\"suggestFinish\":true,\"answerLevel\":\"PASS\",\"qualityHint\":\"좋습니다.\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then — LLM 1회만 호출, 정상 파싱
        verify(claudeAiClient, org.mockito.Mockito.times(1)).chat(any(), any(), any());
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(eval.suggestFinish()).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_evaluateWithAi_1차_파싱_실패시_LLM을_재호출하여_2차_결과를_반환한다")
    void 기능_테스트_evaluateWithAi_1차_파싱_실패시_LLM을_재호출하여_2차_결과를_반환한다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("파싱 불가능한 산문 응답")
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"PASS\",\"qualityHint\":\"재시도 성공.\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then — LLM 2회 호출, 2차 결과로 파싱
        verify(claudeAiClient, org.mockito.Mockito.times(2)).chat(any(), any(), any());
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(eval.qualityHint()).isEqualTo("재시도 성공.");
    }

    @Test
    @DisplayName("기능_테스트_evaluateWithAi_1차_2차_모두_파싱_실패시_fallback을_반환한다")
    void 기능_테스트_evaluateWithAi_1차_2차_모두_파싱_실패시_fallback을_반환한다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("파싱 불가 1")
                .willReturn("파싱 불가 2");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then — 정확히 2회 호출 후 fallback (3회 이상 재시도하지 않음)
        verify(claudeAiClient, org.mockito.Mockito.times(2)).chat(any(), any(), any());
        assertThat(eval.suggestFinish()).isFalse();
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(eval.qualityHint()).isEqualTo("답변이 접수되었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_evaluateWithAi_1차_응답이_빈문자열이면_sanitize가_empty를_내고_재호출한다")
    void 기능_테스트_evaluateWithAi_1차_응답이_빈문자열이면_sanitize가_empty를_내고_재호출한다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("")
                .willReturn("{\"suggestFinish\":false,\"answerLevel\":\"PASS\",\"qualityHint\":\"재시도.\"}");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then — 빈 응답도 파싱 실패로 간주되어 재호출됨
        verify(claudeAiClient, org.mockito.Mockito.times(2)).chat(any(), any(), any());
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
    }

    @Test
    @DisplayName("기능_테스트_evaluateWithAi_markdown_fence로_감싼_응답도_sanitize_후_1회에_파싱한다")
    void 기능_테스트_evaluateWithAi_markdown_fence로_감싼_응답도_sanitize_후_1회에_파싱한다() {
        // given
        InterviewSession session = InterviewSession.builder()
                .user(testUser)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();

        given(claudeAiClient.chat(any(), any(), any()))
                .willReturn("```json\n{\"suggestFinish\":true,\"answerLevel\":\"PASS\",\"qualityHint\":\"좋아요\",}\n```");

        // when
        InterviewEvaluation eval = interviewEvaluationService.evaluateWithAi(session, List.of(), "AI 응답");

        // then — fence/trailing comma가 sanitize되어 재호출 없이 파싱됨
        verify(claudeAiClient, org.mockito.Mockito.times(1)).chat(any(), any(), any());
        assertThat(eval.answerLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(eval.suggestFinish()).isTrue();
    }
}
