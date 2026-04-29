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
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("ClaudeAiClient 테스트")
@ExtendWith(MockitoExtension.class)
class ClaudeAiClientTest {

    interface TestChatModel extends ChatModel, StreamingChatModel {}

    @Mock
    private TestChatModel chatModel;

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

    @Test
    @DisplayName("기능_테스트_streamChat_토큰을_스트리밍_반환한다")
    void 기능_테스트_streamChat_토큰을_스트리밍_반환한다() {
        AssistantMessage msg1 = new AssistantMessage("안녕");
        AssistantMessage msg2 = new AssistantMessage("하세요");
        Generation gen1 = new Generation(msg1);
        Generation gen2 = new Generation(msg2);
        ChatResponse resp1 = new ChatResponse(List.of(gen1));
        ChatResponse resp2 = new ChatResponse(List.of(gen2));

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(resp1, resp2));

        Flux<String> result = claudeAiClient.streamChat("시스템 프롬프트", List.of(), "시작");

        List<String> tokens = result.collectList().block();
        assertThat(tokens).containsExactly("안녕", "하세요");
    }

    @Test
    @DisplayName("기능_테스트_streamChat_빈_토큰은_필터링된다")
    void 기능_테스트_streamChat_빈_토큰은_필터링된다() {
        AssistantMessage msg1 = new AssistantMessage("안녕");
        AssistantMessage msg2 = new AssistantMessage("");
        AssistantMessage msg3 = new AssistantMessage("하세요");
        Generation gen1 = new Generation(msg1);
        Generation gen2 = new Generation(msg2);
        Generation gen3 = new Generation(msg3);
        ChatResponse resp1 = new ChatResponse(List.of(gen1));
        ChatResponse resp2 = new ChatResponse(List.of(gen2));
        ChatResponse resp3 = new ChatResponse(List.of(gen3));

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(resp1, resp2, resp3));

        Flux<String> result = claudeAiClient.streamChat("시스템 프롬프트", List.of(), "시작");

        List<String> tokens = result.collectList().block();
        assertThat(tokens).containsExactly("안녕", "하세요");
    }
}
