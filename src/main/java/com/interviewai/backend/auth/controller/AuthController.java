package com.interviewai.backend.auth.controller;

import com.interviewai.backend.auth.controller.dto.RefreshTokenRequestDto;
import com.interviewai.backend.auth.controller.dto.TokenResponseDto;
import com.interviewai.backend.auth.service.AuthService;
import com.interviewai.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "토큰 갱신")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponseDto>> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDto request) {
        TokenResponseDto response = TokenResponseDto.from(authService.refreshToken(request.getRefreshToken()));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
