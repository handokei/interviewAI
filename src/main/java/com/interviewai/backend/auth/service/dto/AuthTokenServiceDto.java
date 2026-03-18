package com.interviewai.backend.auth.service.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthTokenServiceDto {

    private String accessToken;
    private String refreshToken;
}
