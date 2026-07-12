package com.interviewai.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewai.backend.auth.service.AuthService;
import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;
import com.interviewai.backend.global.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AuthController 테스트")
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // -------------------------------------------------------------------------
    // POST /api/auth/signup — signup
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_이메일_회원가입_정상_요청_시_201과_토큰을_반환한다")
    void 기능_테스트_이메일_회원가입_정상_요청_시_201과_토큰을_반환한다() throws Exception {
        String body = """
                {
                  "email": "user@test.com",
                  "password": "password123",
                  "confirmPassword": "password123",
                  "name": "테스트유저"
                }
                """;
        AuthTokenServiceDto token = AuthTokenServiceDto.builder()
                .accessToken("access.token")
                .refreshToken("refresh.token")
                .build();
        given(authService.signup(eq("user@test.com"), eq("password123"),
                eq("password123"), eq("테스트유저"))).willReturn(token);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"));

        verify(authService).signup(eq("user@test.com"), eq("password123"),
                eq("password123"), eq("테스트유저"));
    }

    @Test
    @DisplayName("예외_테스트_이메일_회원가입_필수_필드_누락_시_400을_반환한다")
    void 예외_테스트_이메일_회원가입_필수_필드_누락_시_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/login — login
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_이메일_로그인_정상_요청_시_200과_토큰을_반환한다")
    void 기능_테스트_이메일_로그인_정상_요청_시_200과_토큰을_반환한다() throws Exception {
        String body = """
                {
                  "email": "user@test.com",
                  "password": "password123"
                }
                """;
        AuthTokenServiceDto token = AuthTokenServiceDto.builder()
                .accessToken("access.token")
                .refreshToken("refresh.token")
                .build();
        given(authService.login(eq("user@test.com"), eq("password123"))).willReturn(token);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"));

        verify(authService).login(eq("user@test.com"), eq("password123"));
    }

    @Test
    @DisplayName("예외_테스트_이메일_로그인_필수_필드_누락_시_400을_반환한다")
    void 예외_테스트_이메일_로그인_필수_필드_누락_시_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
