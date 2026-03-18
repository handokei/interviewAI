package com.interviewai.backend.auth.controller.dto;

import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponseDto {

    private String accessToken;
    private String refreshToken;

    public static TokenResponseDto from(AuthTokenServiceDto dto) {
        return TokenResponseDto.builder()
                .accessToken(dto.getAccessToken())
                .refreshToken(dto.getRefreshToken())
                .build();
    }
}
