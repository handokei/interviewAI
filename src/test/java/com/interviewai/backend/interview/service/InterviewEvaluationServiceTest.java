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
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InterviewEvaluationServiceTest {

    @InjectMocks
    private InterviewEvaluationService interviewEvaluationService;

    @Mock
    private InterviewMessageRepository interviewMessageRepository;

    @Mock
    private ClaudeAiClient claudeAiClient;

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
}
