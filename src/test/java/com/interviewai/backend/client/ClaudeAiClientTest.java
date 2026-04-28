package com.interviewai.backend.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("ClaudeAiClient 테스트")
@ExtendWith(MockitoExtension.class)
class ClaudeAiClientTest {

    @Mock
    private ChatModel chatModel;

    private ClaudeAiClient claudeAiClient;

    @BeforeEach
    void setUp() {
        claudeAiClient = new ClaudeAiClient(chatModel);
    }

    private void mockChatResponse(String responseText) {
        AssistantMessage assistantMessage = new AssistantMessage(responseText);
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Test
    @DisplayName("기능_테스트_summarize_정상_요약을_반환한다")
    void 기능_테스트_summarize_정상_요약을_반환한다() {
        mockChatResponse("이 개발자는 Git Flow 전략을 사용합니다.");

        String result = claudeAiClient.summarize("요약 프롬프트", "GitHub 분석 데이터");

        assertThat(result).isEqualTo("이 개발자는 Git Flow 전략을 사용합니다.");
    }

    @Test
    @DisplayName("기능_테스트_chat_정상_응답을_반환한다")
    void 기능_테스트_chat_정상_응답을_반환한다() {
        mockChatResponse("안녕하세요, 첫 질문입니다.");

        String result = claudeAiClient.chat("시스템 프롬프트", List.of(), "면접을 시작해주세요.");

        assertThat(result).isEqualTo("안녕하세요, 첫 질문입니다.");
    }

    @Test
    @DisplayName("기능_테스트_chat_히스토리가_포함된_대화를_처리한다")
    void 기능_테스트_chat_히스토리가_포함된_대화를_처리한다() {
        mockChatResponse("다음 질문입니다.");

        List<com.interviewai.backend.client.dto.ChatMessage> history = List.of(
                new com.interviewai.backend.client.dto.ChatMessage("user", "안녕하세요"),
                new com.interviewai.backend.client.dto.ChatMessage("assistant", "반갑습니다")
        );

        String result = claudeAiClient.chat("시스템 프롬프트", history, "답변입니다.");

        assertThat(result).isEqualTo("다음 질문입니다.");
    }

    @Test
    @DisplayName("기능_테스트_generateFeedback_정상_피드백을_반환한다")
    void 기능_테스트_generateFeedback_정상_피드백을_반환한다() {
        mockChatResponse("{\"overallScore\": 80}");

        String result = claudeAiClient.generateFeedback("피드백 프롬프트", "대화 내용");

        assertThat(result).isEqualTo("{\"overallScore\": 80}");
    }
}
