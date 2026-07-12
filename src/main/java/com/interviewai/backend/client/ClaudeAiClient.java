package com.interviewai.backend.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.anthropic.AnthropicChatModel;  // [주석 처리 - Anthropic API]
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeAiClient {

    // 스트리밍(stream) 경로는 Spring AI M6의 내장 RetryTemplate(spring.ai.retry)이 적용되지 않는다.
    // (RetryTemplate은 OpenAiChatModel.internalCall에만 감싸져 있고 internalStream에는 없음)
    // 따라서 429/5xx 재시도를 Reactor 레벨에서 직접 붙인다.
    // backoff는 순간적인 RPM 초과(분당 요청 수)를 완화한다.
    // RPD(일일 한도) 소진은 재시도로 회복 불가하므로 여기서 다루지 않고 graceful degradation으로 넘긴다.
    private static final int STREAM_MAX_RETRIES = 3;
    private static final Duration STREAM_RETRY_MIN_BACKOFF = Duration.ofSeconds(2);
    private static final Duration STREAM_RETRY_MAX_BACKOFF = Duration.ofSeconds(8);

    // private final AnthropicChatModel chatModel;  // [주석 처리 - Anthropic API]
    private final ChatModel chatModel;

    public String chat(String systemPrompt, List<com.interviewai.backend.client.dto.ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        for (com.interviewai.backend.client.dto.ChatMessage msg : history) {
            if ("user".equals(msg.role())) {
                messages.add(new UserMessage(msg.content()));
            } else {
                messages.add(new AssistantMessage(msg.content()));
            }
        }
        messages.add(new UserMessage(userMessage));

        Prompt prompt = new Prompt(messages);
        String response = chatModel.call(prompt).getResult().getOutput().getText();
        log.info("Claude response received, length: {}", response != null ? response.length() : 0);
        return response;
    }

    public Flux<String> streamChat(String systemPrompt,
                                   List<com.interviewai.backend.client.dto.ChatMessage> history,
                                   String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        for (com.interviewai.backend.client.dto.ChatMessage msg : history) {
            if ("user".equals(msg.role())) {
                messages.add(new UserMessage(msg.content()));
            } else {
                messages.add(new AssistantMessage(msg.content()));
            }
        }
        messages.add(new UserMessage(userMessage));

        Prompt prompt = new Prompt(messages);
        return ((StreamingChatModel) chatModel)
                .stream(prompt)
                .mapNotNull(res -> res.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isEmpty())
                .retryWhen(Retry.backoff(STREAM_MAX_RETRIES, STREAM_RETRY_MIN_BACKOFF)
                        .maxBackoff(STREAM_RETRY_MAX_BACKOFF)
                        .filter(ClaudeAiClient::isRetryable)
                        .doBeforeRetry(signal -> log.warn("스트리밍 LLM 호출 재시도 {}회차: {}",
                                signal.totalRetries() + 1, signal.failure().getMessage())));
    }

    /**
     * 재시도 대상 여부: HTTP 429(Too Many Requests) 또는 5xx.
     * 429는 토큰이 전혀 방출되기 전 요청 단위 거부이므로 재시도가 안전하다(중복 출력 없음).
     */
    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            return status == 429 || wcre.getStatusCode().is5xxServerError();
        }
        return false;
    }

    public String generateFeedback(String systemPrompt, String conversationText) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(conversationText));

        Prompt prompt = new Prompt(messages);
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    public String summarize(String systemPrompt, String content) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(content));

        Prompt prompt = new Prompt(messages);
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
