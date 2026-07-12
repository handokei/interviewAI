package com.interviewai.backend.user.controller;

import com.interviewai.backend.global.config.TestSecurityConfig;
import com.interviewai.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("UserController 테스트")
@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private UsernamePasswordAuthenticationToken auth;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        auth = new UsernamePasswordAuthenticationToken(
                USER_ID,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    // -------------------------------------------------------------------------
    // PATCH /api/users/me/password — changePassword
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_비밀번호_변경_정상_요청_시_200을_반환한다")
    void 기능_테스트_비밀번호_변경_정상_요청_시_200을_반환한다() throws Exception {
        String body = """
                {
                  "currentPassword": "oldpass123",
                  "newPassword": "newpass123",
                  "confirmNewPassword": "newpass123"
                }
                """;
        willDoNothing().given(userService)
                .changePassword(eq(USER_ID), eq("oldpass123"), eq("newpass123"), eq("newpass123"));

        mockMvc.perform(patch("/api/users/me/password")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService)
                .changePassword(eq(USER_ID), eq("oldpass123"), eq("newpass123"), eq("newpass123"));
    }

    @Test
    @DisplayName("예외_테스트_비밀번호_변경_필수_필드_누락_시_400을_반환한다")
    void 예외_테스트_비밀번호_변경_필수_필드_누락_시_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
