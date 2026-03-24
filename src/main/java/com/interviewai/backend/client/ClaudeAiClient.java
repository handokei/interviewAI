package com.interviewai.backend.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.anthropic.AnthropicChatModel;  // [주석 처리 - Anthropic API]
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeAiClient {

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

    public String generateFeedback(String systemPrompt, String conversationText) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(conversationText));

        Prompt prompt = new Prompt(messages);
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
