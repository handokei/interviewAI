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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    private ChatResponse chatResponseOf(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    @DisplayName("기능_테스트_streamChat_429_1회_후_성공하면_재시도로_최종_성공한다")
    void 기능_테스트_streamChat_429_1회_후_성공하면_재시도로_최종_성공한다() {
        WebClientResponseException tooManyRequests = WebClientResponseException.create(
                429, "Too Many Requests", HttpHeaders.EMPTY, null, null);
        AtomicInteger attempts = new AtomicInteger(0);

        // stream()은 조립 시 1회만 호출되고, retryWhen은 반환된 publisher를 재구독한다.
        // 따라서 구독마다 동작이 달라지도록 defer로 감싼다: 첫 구독은 429, 재구독은 성공.
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.defer(() -> {
            if (attempts.getAndIncrement() == 0) {
                return Flux.error(tooManyRequests);
            }
            return Flux.just(chatResponseOf("안녕"), chatResponseOf("하세요"));
        }));

        StepVerifier.withVirtualTime(() ->
                        claudeAiClient.streamChat("시스템 프롬프트", List.of(), "시작"))
                .thenAwait(Duration.ofSeconds(30))
                .expectNext("안녕", "하세요")
                .verifyComplete();

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("예외_테스트_streamChat_429가_계속되면_재시도_소진_후_오류로_종료된다")
    void 예외_테스트_streamChat_429가_계속되면_재시도_소진_후_오류로_종료된다() {
        WebClientResponseException tooManyRequests = WebClientResponseException.create(
                429, "Too Many Requests", HttpHeaders.EMPTY, null, null);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(tooManyRequests));

        StepVerifier.withVirtualTime(() ->
                        claudeAiClient.streamChat("시스템 프롬프트", List.of(), "시작"))
                .thenAwait(Duration.ofSeconds(60))
                .expectErrorSatisfies(error ->
                        assertThat(error).hasMessageContaining("Retries exhausted"))
                .verify();
    }

    @Test
    @DisplayName("예외_테스트_streamChat_재시도_대상이_아닌_오류는_즉시_전파된다")
    void 예외_테스트_streamChat_재시도_대상이_아닌_오류는_즉시_전파된다() {
        WebClientResponseException badRequest = WebClientResponseException.create(
                400, "Bad Request", HttpHeaders.EMPTY, null, null);
        AtomicInteger subscriptions = new AtomicInteger(0);
        // defer로 감싸 구독 횟수를 측정한다. 재시도 대상이 아니면 재구독이 없어야 한다.
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.error(badRequest);
        }));

        StepVerifier.create(claudeAiClient.streamChat("시스템 프롬프트", List.of(), "시작"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(WebClientResponseException.class);
                    assertThat(((WebClientResponseException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();

        // 재시도(재구독) 없이 단 1회만 구독된다
        assertThat(subscriptions.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("예외_테스트_streamChat_WebClient가_아닌_오류는_재시도하지_않고_즉시_전파된다")
    void 예외_테스트_streamChat_WebClient가_아닌_오류는_재시도하지_않고_즉시_전파된다() {
        RuntimeException nonHttpError = new IllegalStateException("파싱 오류");
        AtomicInteger subscriptions = new AtomicInteger(0);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.error(nonHttpError);
        }));

        StepVerifier.create(claudeAiClient.streamChat("시스템 프롬프트", List.of(), "시작"))
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(IllegalStateException.class))
                .verify();

        assertThat(subscriptions.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("기능_테스트_streamChat_히스토리가_포함된_경우도_토큰을_반환한다")
    void 기능_테스트_streamChat_히스토리가_포함된_경우도_토큰을_반환한다() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chatResponseOf("응답")));

        List<com.interviewai.backend.client.dto.ChatMessage> history = List.of(
                new com.interviewai.backend.client.dto.ChatMessage("user", "질문"),
                new com.interviewai.backend.client.dto.ChatMessage("assistant", "이전 답변")
        );

        List<String> tokens = claudeAiClient.streamChat("시스템 프롬프트", history, "답변")
                .collectList().block();

        assertThat(tokens).containsExactly("응답");
    }

    @Test
    @DisplayName("기능_테스트_streamChat_5xx_오류도_재시도_대상이다")
    void 기능_테스트_streamChat_5xx_오류도_재시도_대상이다() {
        WebClientResponseException serverError = WebClientResponseException.create(
                503, "Service Unavailable", HttpHeaders.EMPTY, null, null);
        AtomicInteger attempts = new AtomicInteger(0);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.defer(() -> {
            if (attempts.getAndIncrement() == 0) {
                return Flux.error(serverError);
            }
            return Flux.just(chatResponseOf("복구"));
        }));

        StepVerifier.withVirtualTime(() ->
                        claudeAiClient.streamChat("시스템 프롬프트", List.of(), "시작"))
                .thenAwait(Duration.ofSeconds(30))
                .expectNext("복구")
                .verifyComplete();

        assertThat(attempts.get()).isEqualTo(2);
    }
}
