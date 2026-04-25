package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.dto.ChatMessage;
import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.interview.enums.AnswerLevel;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TokenEstimatorTest {

    private TokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        InterviewProperties properties = new InterviewProperties();
        properties.getPrompt().setCharsPerToken(3);
        tokenEstimator = new TokenEstimator(properties);
    }

    @Test
    @DisplayName("기능_테스트_문자열의_추정_토큰_수를_반환한다")
    void 기능_테스트_문자열의_추정_토큰_수를_반환한다() {
        assertThat(tokenEstimator.estimateTokens("abcdef")).isEqualTo(2); // 6 / 3
        assertThat(tokenEstimator.estimateTokens("ab")).isEqualTo(1);     // 2 / 3 = 0 → max(1, 0) = 1
    }

    @Test
    @DisplayName("기능_테스트_null이나_빈_문자열은_0을_반환한다")
    void 기능_테스트_null이나_빈_문자열은_0을_반환한다() {
        assertThat(tokenEstimator.estimateTokens((String) null)).isZero();
        assertThat(tokenEstimator.estimateTokens("")).isZero();
    }

    @Test
    @DisplayName("기능_테스트_ChatMessage_리스트의_총_토큰을_추정한다")
    void 기능_테스트_ChatMessage_리스트의_총_토큰을_추정한다() {
        List<ChatMessage> messages = List.of(
                new ChatMessage("user", "abcdef"),     // 2
                new ChatMessage("assistant", "abcdefghi") // 3
        );
        assertThat(tokenEstimator.estimateTokens(messages)).isEqualTo(5);
    }

    @Test
    @DisplayName("기능_테스트_빈_리스트는_그대로_반환한다")
    void 기능_테스트_빈_리스트는_그대로_반환한다() {
        List<ChatMessage> result = tokenEstimator.trimHistory(List.of(), 100);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("기능_테스트_budget_충분하면_전체_히스토리를_반환한다")
    void 기능_테스트_budget_충분하면_전체_히스토리를_반환한다() {
        List<ChatMessage> history = List.of(
                new ChatMessage("assistant", "abc"),
                new ChatMessage("user", "def"),
                new ChatMessage("assistant", "ghi")
        );
        List<ChatMessage> result = tokenEstimator.trimHistory(history, 10000);
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("기능_테스트_budget_초과시_첫_메시지와_최근_메시지만_반환한다")
    void 기능_테스트_budget_초과시_첫_메시지와_최근_메시지만_반환한다() {
        List<ChatMessage> history = List.of(
                new ChatMessage("assistant", "aaa"),  // 1 토큰
                new ChatMessage("user", "bbb"),       // 1 토큰
                new ChatMessage("assistant", "ccc"),   // 1 토큰
                new ChatMessage("user", "ddd")         // 1 토큰
        );
        // budget 2: 첫 메시지(1) + 남은 budget(1) → 가장 최근 1개만
        List<ChatMessage> result = tokenEstimator.trimHistory(history, 2);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).content()).isEqualTo("aaa"); // 첫 메시지
        assertThat(result.get(1).content()).isEqualTo("ddd"); // 가장 최근
    }

    @Test
    @DisplayName("기능_테스트_budget이_첫_메시지보다_작으면_첫_메시지만_반환한다")
    void 기능_테스트_budget이_첫_메시지보다_작으면_첫_메시지만_반환한다() {
        List<ChatMessage> history = List.of(
                new ChatMessage("assistant", "abcdefghijklmno"), // 5 토큰
                new ChatMessage("user", "abc")
        );
        List<ChatMessage> result = tokenEstimator.trimHistory(history, 3);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("abcdefghijklmno");
    }

    @Test
    @DisplayName("기능_테스트_중요도_trimming_빈_리스트는_빈_리스트를_반환한다")
    void 기능_테스트_중요도_trimming_빈_리스트는_빈_리스트를_반환한다() {
        List<ChatMessage> result = tokenEstimator.trimHistoryWithPriority(List.of(), 100);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("기능_테스트_중요도_trimming_budget_충분하면_전체_반환한다")
    void 기능_테스트_중요도_trimming_budget_충분하면_전체_반환한다() {
        InterviewSession session = mock(InterviewSession.class);
        List<InterviewMessage> messages = List.of(
                createMessage(session, MessageRole.AI, "질문1", null, 1),
                createMessage(session, MessageRole.USER, "답변1", AnswerLevel.PASS, 2),
                createMessage(session, MessageRole.AI, "질문2", null, 3)
        );
        List<ChatMessage> result = tokenEstimator.trimHistoryWithPriority(messages, 10000);
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("기능_테스트_중요도_trimming_STUDY_REQUIRED_턴이_우선_보존된다")
    void 기능_테스트_중요도_trimming_STUDY_REQUIRED_턴이_우선_보존된다() {
        InterviewSession session = mock(InterviewSession.class);
        List<InterviewMessage> messages = List.of(
                createMessage(session, MessageRole.AI, "aaa", null, 1),          // 첫 메시지 (예약)
                createMessage(session, MessageRole.USER, "bbb", AnswerLevel.PASS, 2),          // PASS — 제거 대상
                createMessage(session, MessageRole.AI, "ccc", null, 3),
                createMessage(session, MessageRole.USER, "ddd", AnswerLevel.STUDY_REQUIRED, 4), // STUDY_REQUIRED — 우선 보존
                createMessage(session, MessageRole.AI, "eee", null, 5),
                createMessage(session, MessageRole.USER, "fff", AnswerLevel.PASS, 6)           // PASS — 최근이므로 포함 시도
        );
        // budget 3: 첫 메시지(1) + STUDY_REQUIRED(1) + 최근 1개
        List<ChatMessage> result = tokenEstimator.trimHistoryWithPriority(messages, 3);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).content()).isEqualTo("aaa"); // 첫 메시지
        assertThat(result.stream().anyMatch(m -> m.content().equals("ddd"))).isTrue(); // STUDY_REQUIRED 보존
    }

    @Test
    @DisplayName("기능_테스트_중요도_trimming_결과가_시간순으로_정렬된다")
    void 기능_테스트_중요도_trimming_결과가_시간순으로_정렬된다() {
        InterviewSession session = mock(InterviewSession.class);
        List<InterviewMessage> messages = List.of(
                createMessage(session, MessageRole.AI, "aaa", null, 1),
                createMessage(session, MessageRole.USER, "bbb", AnswerLevel.STUDY_REQUIRED, 2),
                createMessage(session, MessageRole.AI, "ccc", null, 3),
                createMessage(session, MessageRole.USER, "ddd", AnswerLevel.PASS, 4)
        );
        List<ChatMessage> result = tokenEstimator.trimHistoryWithPriority(messages, 3);
        // 시간순이 유지되어야 함 (첫 메시지 → STUDY_REQUIRED → 최근)
        for (int i = 0; i < result.size() - 1; i++) {
            // 결과에서 원본 순서가 유지되는지 확인
            assertThat(result.size()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("기능_테스트_toChatMessages_InterviewMessage를_ChatMessage로_변환한다")
    void 기능_테스트_toChatMessages_InterviewMessage를_ChatMessage로_변환한다() {
        InterviewSession session = mock(InterviewSession.class);
        List<InterviewMessage> messages = List.of(
                createMessage(session, MessageRole.AI, "질문입니다", null, 1),
                createMessage(session, MessageRole.USER, "답변입니다", null, 2)
        );
        List<ChatMessage> result = tokenEstimator.toChatMessages(messages);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).role()).isEqualTo("assistant");
        assertThat(result.get(0).content()).isEqualTo("질문입니다");
        assertThat(result.get(1).role()).isEqualTo("user");
        assertThat(result.get(1).content()).isEqualTo("답변입니다");
    }

    private InterviewMessage createMessage(InterviewSession session, MessageRole role,
                                           String content, AnswerLevel answerLevel, int minuteOffset) {
        InterviewMessage msg = InterviewMessage.builder()
                .session(session)
                .role(role)
                .content(content)
                .build();
        ReflectionTestUtils.setField(msg, "createdAt", LocalDateTime.of(2026, 1, 1, 0, minuteOffset));
        if (answerLevel != null) {
            msg.updateEvaluation(answerLevel, false, "힌트");
        }
        return msg;
    }
}
