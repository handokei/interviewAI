package com.interviewai.backend.interview.model;

import com.interviewai.backend.interview.enums.AnswerLevel;
import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewMessage 모델 테스트")
class InterviewMessageTest {

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
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .build();
    }

    private void setField(Object obj, String fieldName, Object value) {
        ReflectionTestUtils.setField(obj, fieldName, value);
    }

    @Test
    @DisplayName("기능_테스트_updateEvaluation_호출_시_모든_평가_필드가_설정된다")
    void 기능_테스트_updateEvaluation_호출_시_모든_평가_필드가_설정된다() {
        // given
        InterviewMessage message = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.AI)
                .content("면접 질문입니다.")
                .build();

        // when
        message.updateEvaluation(AnswerLevel.PASS, true, "훌륭한 답변이었습니다.");

        // then
        assertThat(message.getAnswerLevel()).isEqualTo(AnswerLevel.PASS);
        assertThat(message.getSuggestFinish()).isTrue();
        assertThat(message.getQualityHint()).isEqualTo("훌륭한 답변이었습니다.");
    }

    @Test
    @DisplayName("기능_테스트_updateEvaluation_NEEDS_IMPROVEMENT와_suggestFinish_false로_설정된다")
    void 기능_테스트_updateEvaluation_NEEDS_IMPROVEMENT와_suggestFinish_false로_설정된다() {
        // given
        InterviewMessage message = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.AI)
                .content("면접 질문입니다.")
                .build();

        // when
        message.updateEvaluation(AnswerLevel.NEEDS_IMPROVEMENT, false, "기초 개념 보완이 필요합니다.");

        // then
        assertThat(message.getAnswerLevel()).isEqualTo(AnswerLevel.NEEDS_IMPROVEMENT);
        assertThat(message.getSuggestFinish()).isFalse();
        assertThat(message.getQualityHint()).isEqualTo("기초 개념 보완이 필요합니다.");
    }

    @Test
    @DisplayName("기능_테스트_updateEvaluation_STUDY_REQUIRED_레벨로_설정된다")
    void 기능_테스트_updateEvaluation_STUDY_REQUIRED_레벨로_설정된다() {
        // given
        InterviewMessage message = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.AI)
                .content("면접 질문입니다.")
                .build();

        // when
        message.updateEvaluation(AnswerLevel.STUDY_REQUIRED, false, "개념 이해가 부족합니다.");

        // then
        assertThat(message.getAnswerLevel()).isEqualTo(AnswerLevel.STUDY_REQUIRED);
        assertThat(message.getSuggestFinish()).isFalse();
        assertThat(message.getQualityHint()).isEqualTo("개념 이해가 부족합니다.");
    }

    @Test
    @DisplayName("기능_테스트_isEvaluated_answerLevel이_null이면_false를_반환한다")
    void 기능_테스트_isEvaluated_answerLevel이_null이면_false를_반환한다() {
        // given
        InterviewMessage message = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.AI)
                .content("면접 질문입니다.")
                .build();
        // answerLevel 설정 없음 — null

        // when
        boolean result = message.isEvaluated();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("기능_테스트_isEvaluated_answerLevel이_설정되면_true를_반환한다")
    void 기능_테스트_isEvaluated_answerLevel이_설정되면_true를_반환한다() {
        // given
        InterviewMessage message = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.AI)
                .content("면접 질문입니다.")
                .build();
        message.updateEvaluation(AnswerLevel.PASS, false, "좋습니다.");

        // when
        boolean result = message.isEvaluated();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_USER_역할_메시지를_빌드할_수_있다")
    void 기능_테스트_USER_역할_메시지를_빌드할_수_있다() {
        // given & when
        InterviewMessage message = InterviewMessage.builder()
                .session(testSession)
                .role(MessageRole.USER)
                .content("저는 Java를 5년간 사용했습니다.")
                .build();

        // then
        assertThat(message.getSession()).isEqualTo(testSession);
        assertThat(message.getRole()).isEqualTo(MessageRole.USER);
        assertThat(message.getContent()).isEqualTo("저는 Java를 5년간 사용했습니다.");
        assertThat(message.getAnswerLevel()).isNull();
        assertThat(message.getSuggestFinish()).isNull();
        assertThat(message.getQualityHint()).isNull();
    }
}
