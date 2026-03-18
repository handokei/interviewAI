package com.interviewai.backend.auth.service;

import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;

public interface AuthService {

    AuthTokenServiceDto refreshToken(String refreshToken);
}
