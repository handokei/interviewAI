package com.interviewai.backend.auth.service;

import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;

public interface AuthService {

    AuthTokenServiceDto refreshToken(String refreshToken);

    AuthTokenServiceDto signup(String email, String password, String confirmPassword, String name);

    AuthTokenServiceDto login(String email, String password);
}
