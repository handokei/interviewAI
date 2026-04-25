package com.interviewai.backend.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewai.backend.global.config.TestSecurityConfig;
import com.interviewai.backend.interview.controller.dto.*;
import com.interviewai.backend.interview.enums.AnswerLevel;
import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import com.interviewai.backend.interview.enums.InterviewStatus;
import com.interviewai.backend.interview.service.InterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("InterviewController 테스트")
@WebMvcTest(InterviewController.class)
@Import(TestSecurityConfig.class)
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InterviewService interviewService;

    private UsernamePasswordAuthenticationToken auth;
    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @BeforeEach
    void setUp() {
        auth = new UsernamePasswordAuthenticationToken(
                USER_ID,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    // -------------------------------------------------------------------------
    // POST /api/interviews — startInterview
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_면접_시작_정상_요청_시_200과_sessionId를_반환한다")
    void 기능_테스트_면접_시작_정상_요청_시_200과_sessionId를_반환한다() throws Exception {
        InterviewStartRequestDto request = buildStartRequest();
        InterviewStartResponseDto response = InterviewStartResponseDto.builder()
                .sessionId(SESSION_ID)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .status(InterviewStatus.IN_PROGRESS)
                .firstQuestion("자기소개를 해주세요.")
                .createdAt(LocalDateTime.now())
                .build();

        given(interviewService.startInterview(eq(USER_ID), any())).willReturn(response);

        mockMvc.perform(post("/api/interviews")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID));

        verify(interviewService).startInterview(eq(USER_ID), any());
    }

    @Test
    @DisplayName("예외_테스트_면접_시작_필수_필드_누락_시_400을_반환한다")
    void 예외_테스트_면접_시작_필수_필드_누락_시_400을_반환한다() throws Exception {
        String invalidBody = "{}";

        mockMvc.perform(post("/api/interviews")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /api/interviews/{sessionId}/first-question/stream — streamFirstQuestion
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_첫_질문_스트리밍_정상_요청_시_SseEmitter를_반환한다")
    void 기능_테스트_첫_질문_스트리밍_정상_요청_시_SseEmitter를_반환한다() throws Exception {
        SseEmitter emitter = new SseEmitter();
        given(interviewService.streamFirstQuestion(eq(USER_ID), eq(SESSION_ID))).willReturn(emitter);

        MvcResult result = mockMvc.perform(post("/api/interviews/{sessionId}/first-question/stream", SESSION_ID)
                        .with(authentication(auth))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        verify(interviewService).streamFirstQuestion(eq(USER_ID), eq(SESSION_ID));
    }

    @Test
    @DisplayName("기능_테스트_첫_질문_스트리밍_서비스가_올바른_userId와_sessionId로_호출된다")
    void 기능_테스트_첫_질문_스트리밍_서비스가_올바른_userId와_sessionId로_호출된다() throws Exception {
        Long otherSessionId = 99L;
        Long otherUserId = 42L;
        UsernamePasswordAuthenticationToken otherAuth = new UsernamePasswordAuthenticationToken(
                otherUserId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SseEmitter emitter = new SseEmitter();
        given(interviewService.streamFirstQuestion(eq(otherUserId), eq(otherSessionId))).willReturn(emitter);

        mockMvc.perform(post("/api/interviews/{sessionId}/first-question/stream", otherSessionId)
                        .with(authentication(otherAuth))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(interviewService).streamFirstQuestion(eq(otherUserId), eq(otherSessionId));
    }

    // -------------------------------------------------------------------------
    // POST /api/interviews/{sessionId}/messages — sendMessage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_메시지_전송_정상_요청_시_200과_응답을_반환한다")
    void 기능_테스트_메시지_전송_정상_요청_시_200과_응답을_반환한다() throws Exception {
        InterviewSendMessageRequestDto request = buildSendMessageRequest("좋은 답변입니다.");
        InterviewSendMessageResponseDto response = InterviewSendMessageResponseDto.builder()
                .aiResponse("다음 질문입니다.")
                .isCompleted(false)
                .suggestFinish(false)
                .answerLevel(AnswerLevel.PASS)
                .qualityHint("구체적인 예시를 들어주세요.")
                .build();

        given(interviewService.sendMessage(eq(USER_ID), eq(SESSION_ID), any())).willReturn(response);

        mockMvc.perform(post("/api/interviews/{sessionId}/messages", SESSION_ID)
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.aiResponse").value("다음 질문입니다."));

        verify(interviewService).sendMessage(eq(USER_ID), eq(SESSION_ID), any());
    }

    @Test
    @DisplayName("예외_테스트_메시지_전송_content_누락_시_400을_반환한다")
    void 예외_테스트_메시지_전송_content_누락_시_400을_반환한다() throws Exception {
        String invalidBody = "{}";

        mockMvc.perform(post("/api/interviews/{sessionId}/messages", SESSION_ID)
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /api/interviews/{sessionId}/messages/stream — streamMessage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_메시지_스트리밍_정상_요청_시_SseEmitter를_반환한다")
    void 기능_테스트_메시지_스트리밍_정상_요청_시_SseEmitter를_반환한다() throws Exception {
        InterviewSendMessageRequestDto request = buildSendMessageRequest("스트리밍 답변");
        SseEmitter emitter = new SseEmitter();
        given(interviewService.streamMessage(eq(USER_ID), eq(SESSION_ID), any())).willReturn(emitter);

        mockMvc.perform(post("/api/interviews/{sessionId}/messages/stream", SESSION_ID)
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(interviewService).streamMessage(eq(USER_ID), eq(SESSION_ID), any());
    }

    // -------------------------------------------------------------------------
    // POST /api/interviews/{sessionId}/finish — finishInterview
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_면접_종료_정상_요청_시_200과_피드백을_반환한다")
    void 기능_테스트_면접_종료_정상_요청_시_200과_피드백을_반환한다() throws Exception {
        InterviewFeedbackResponseDto response = InterviewFeedbackResponseDto.builder()
                .id(1L)
                .sessionId(SESSION_ID)
                .overallLevel(AnswerLevel.PASS)
                .strengths("논리적입니다.")
                .improvements("구체성이 필요합니다.")
                .fullReport("전체 보고서")
                .createdAt(LocalDateTime.now())
                .build();

        given(interviewService.finishInterview(eq(USER_ID), eq(SESSION_ID))).willReturn(response);

        mockMvc.perform(post("/api/interviews/{sessionId}/finish", SESSION_ID)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID));

        verify(interviewService).finishInterview(eq(USER_ID), eq(SESSION_ID));
    }

    // -------------------------------------------------------------------------
    // GET /api/interviews — getMySessions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_면접_이력_목록_정상_요청_시_200과_페이지를_반환한다")
    void 기능_테스트_면접_이력_목록_정상_요청_시_200과_페이지를_반환한다() throws Exception {
        InterviewSessionResponseDto sessionDto = InterviewSessionResponseDto.builder()
                .id(SESSION_ID)
                .mode(InterviewMode.BASIC)
                .level(InterviewLevel.JUNIOR)
                .status(InterviewStatus.IN_PROGRESS)
                .jobTitle("백엔드 개발자")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Page<InterviewSessionResponseDto> page = new PageImpl<>(
                List.of(sessionDto), PageRequest.of(0, 10), 1);

        given(interviewService.findMySessions(eq(USER_ID), any())).willReturn(page);

        mockMvc.perform(get("/api/interviews")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(SESSION_ID));

        verify(interviewService).findMySessions(eq(USER_ID), any());
    }

    // -------------------------------------------------------------------------
    // GET /api/interviews/{sessionId}/messages — getSessionMessages
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_면접_대화_내용_조회_정상_요청_시_200과_메시지_목록을_반환한다")
    void 기능_테스트_면접_대화_내용_조회_정상_요청_시_200과_메시지_목록을_반환한다() throws Exception {
        List<InterviewMessageResponseDto> messages = List.of();
        given(interviewService.findSessionMessages(eq(USER_ID), eq(SESSION_ID))).willReturn(messages);

        mockMvc.perform(get("/api/interviews/{sessionId}/messages", SESSION_ID)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        verify(interviewService).findSessionMessages(eq(USER_ID), eq(SESSION_ID));
    }

    // -------------------------------------------------------------------------
    // GET /api/interviews/{sessionId}/feedback — getFeedback
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_피드백_조회_정상_요청_시_200과_피드백을_반환한다")
    void 기능_테스트_피드백_조회_정상_요청_시_200과_피드백을_반환한다() throws Exception {
        InterviewFeedbackResponseDto response = InterviewFeedbackResponseDto.builder()
                .id(2L)
                .sessionId(SESSION_ID)
                .overallLevel(AnswerLevel.PASS)
                .strengths("뛰어난 논리력")
                .improvements("없음")
                .fullReport("우수한 면접이었습니다.")
                .createdAt(LocalDateTime.now())
                .build();

        given(interviewService.findFeedback(eq(USER_ID), eq(SESSION_ID))).willReturn(response);

        mockMvc.perform(get("/api/interviews/{sessionId}/feedback", SESSION_ID)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2L));

        verify(interviewService).findFeedback(eq(USER_ID), eq(SESSION_ID));
    }

    // -------------------------------------------------------------------------
    // PATCH /api/interviews/{sessionId}/cancel — cancelInterview
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_면접_취소_정상_요청_시_200과_취소_메시지를_반환한다")
    void 기능_테스트_면접_취소_정상_요청_시_200과_취소_메시지를_반환한다() throws Exception {
        willDoNothing().given(interviewService).cancelInterview(eq(USER_ID), eq(SESSION_ID));

        mockMvc.perform(patch("/api/interviews/{sessionId}/cancel", SESSION_ID)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("면접이 취소되었습니다."));

        verify(interviewService).cancelInterview(eq(USER_ID), eq(SESSION_ID));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/interviews/{sessionId} — deleteInterview
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_면접_삭제_정상_요청_시_200과_삭제_메시지를_반환한다")
    void 기능_테스트_면접_삭제_정상_요청_시_200과_삭제_메시지를_반환한다() throws Exception {
        willDoNothing().given(interviewService).deleteInterview(eq(USER_ID), eq(SESSION_ID));

        mockMvc.perform(delete("/api/interviews/{sessionId}", SESSION_ID)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("면접이 삭제되었습니다."));

        verify(interviewService).deleteInterview(eq(USER_ID), eq(SESSION_ID));
    }

    // -------------------------------------------------------------------------
    // POST /api/interviews/batch-delete — deleteInterviews
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_면접_다중_삭제_정상_요청_시_200과_삭제_메시지를_반환한다")
    void 기능_테스트_면접_다중_삭제_정상_요청_시_200과_삭제_메시지를_반환한다() throws Exception {
        String body = "{\"sessionIds\": [1, 2, 3]}";
        willDoNothing().given(interviewService).deleteInterviews(eq(USER_ID), any());

        mockMvc.perform(post("/api/interviews/batch-delete")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("선택한 면접이 삭제되었습니다."));

        verify(interviewService).deleteInterviews(eq(USER_ID), any());
    }

    @Test
    @DisplayName("예외_테스트_면접_다중_삭제_sessionIds_누락_시_400을_반환한다")
    void 예외_테스트_면접_다중_삭제_sessionIds_누락_시_400을_반환한다() throws Exception {
        String invalidBody = "{}";

        mockMvc.perform(post("/api/interviews/batch-delete")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/interviews/{sessionId}/messages/latest-eval — getLatestEvaluation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_최신_AI_메시지_평가_조회_정상_요청_시_200과_평가_결과를_반환한다")
    void 기능_테스트_최신_AI_메시지_평가_조회_정상_요청_시_200과_평가_결과를_반환한다() throws Exception {
        InterviewMessageEvalResponseDto response = InterviewMessageEvalResponseDto.builder()
                .evaluated(true)
                .suggestFinish(false)
                .answerLevel(AnswerLevel.PASS)
                .qualityHint("구체적인 예시를 들어주세요.")
                .build();

        given(interviewService.getLatestEvaluation(eq(USER_ID), eq(SESSION_ID))).willReturn(response);

        mockMvc.perform(get("/api/interviews/{sessionId}/messages/latest-eval", SESSION_ID)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evaluated").value(true))
                .andExpect(jsonPath("$.data.answerLevel").value("PASS"))
                .andExpect(jsonPath("$.data.qualityHint").value("구체적인 예시를 들어주세요."));

        verify(interviewService).getLatestEvaluation(eq(USER_ID), eq(SESSION_ID));
    }

    @Test
    @DisplayName("기능_테스트_최신_AI_메시지_평가_미완료_시_evaluated_false를_반환한다")
    void 기능_테스트_최신_AI_메시지_평가_미완료_시_evaluated_false를_반환한다() throws Exception {
        InterviewMessageEvalResponseDto response = InterviewMessageEvalResponseDto.builder()
                .evaluated(false)
                .suggestFinish(null)
                .answerLevel(null)
                .qualityHint(null)
                .build();

        given(interviewService.getLatestEvaluation(eq(USER_ID), eq(SESSION_ID))).willReturn(response);

        mockMvc.perform(get("/api/interviews/{sessionId}/messages/latest-eval", SESSION_ID)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evaluated").value(false))
                .andExpect(jsonPath("$.data.answerLevel").doesNotExist());

        verify(interviewService).getLatestEvaluation(eq(USER_ID), eq(SESSION_ID));
    }

    // -------------------------------------------------------------------------
    // GET /api/interviews/stats — getMyStats
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_내_면접_통계_조회_정상_요청_시_200과_통계를_반환한다")
    void 기능_테스트_내_면접_통계_조회_정상_요청_시_200과_통계를_반환한다() throws Exception {
        InterviewStatsResponseDto response = InterviewStatsResponseDto.builder()
                .totalCount(5L)
                .completedCount(3L)
                .cancelledCount(1L)
                .modeDistribution(Map.of("BASIC", 3L, "RESUME", 2L))
                .levelDistribution(Map.of("JUNIOR", 4L, "SENIOR", 1L))
                .build();

        given(interviewService.getMyStats(eq(USER_ID))).willReturn(response);

        mockMvc.perform(get("/api/interviews/stats")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(5));

        verify(interviewService).getMyStats(eq(USER_ID));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InterviewStartRequestDto buildStartRequest() throws Exception {
        String json = """
                {
                  "mode": "BASIC",
                  "level": "JUNIOR",
                  "jobTitle": "백엔드 개발자"
                }
                """;
        return objectMapper.readValue(json, InterviewStartRequestDto.class);
    }

    private InterviewSendMessageRequestDto buildSendMessageRequest(String content) throws Exception {
        String json = String.format("{\"content\": \"%s\"}", content);
        return objectMapper.readValue(json, InterviewSendMessageRequestDto.class);
    }
}
