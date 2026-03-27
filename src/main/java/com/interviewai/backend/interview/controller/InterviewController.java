package com.interviewai.backend.interview.controller;

import com.interviewai.backend.common.response.ApiResponse;
import com.interviewai.backend.interview.controller.dto.*;
import com.interviewai.backend.interview.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "Interview", description = "면접 API")
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @Operation(summary = "면접 시작")
    @PostMapping
    public ResponseEntity<ApiResponse<InterviewStartResponseDto>> startInterview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InterviewStartRequestDto request) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.startInterview(userId, request)));
    }

    @Operation(summary = "메시지 전송 (답변)")
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<InterviewSendMessageResponseDto>> sendMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody InterviewSendMessageRequestDto request) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.sendMessage(userId, sessionId, request)));
    }

    @Operation(summary = "메시지 전송 (답변) — 스트리밍")
    @PostMapping(value = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody InterviewSendMessageRequestDto request) {
        return interviewService.streamMessage(userId, sessionId, request);
    }

    @Operation(summary = "면접 종료 및 피드백 생성")
    @PostMapping("/{sessionId}/finish")
    public ResponseEntity<ApiResponse<InterviewFeedbackResponseDto>> finishInterview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.finishInterview(userId, sessionId)));
    }

    @Operation(summary = "내 면접 이력 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InterviewSessionResponseDto>>> getMySessions(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.findMySessions(userId, pageable)));
    }

    @Operation(summary = "면접 대화 내용 조회")
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<InterviewMessageResponseDto>>> getSessionMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.findSessionMessages(userId, sessionId)));
    }

    @Operation(summary = "피드백 조회")
    @GetMapping("/{sessionId}/feedback")
    public ResponseEntity<ApiResponse<InterviewFeedbackResponseDto>> getFeedback(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.findFeedback(userId, sessionId)));
    }

    @Operation(summary = "면접 취소")
    @PatchMapping("/{sessionId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelInterview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        interviewService.cancelInterview(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null, "면접이 취소되었습니다."));
    }

    @Operation(summary = "면접 삭제")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteInterview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        interviewService.deleteInterview(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null, "면접이 삭제되었습니다."));
    }

    @Operation(summary = "면접 다중 삭제")
    @PostMapping("/batch-delete")
    public ResponseEntity<ApiResponse<Void>> deleteInterviews(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InterviewBatchDeleteRequestDto request) {
        interviewService.deleteInterviews(userId, request.getSessionIds());
        return ResponseEntity.ok(ApiResponse.ok(null, "선택한 면접이 삭제되었습니다."));
    }

    @Operation(summary = "내 면접 통계 조회")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<InterviewStatsResponseDto>> getMyStats(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.getMyStats(userId)));
    }
}
