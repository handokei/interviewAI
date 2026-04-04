package com.interviewai.backend.global.config.oauth;

import com.interviewai.backend.global.config.jwt.JwtProvider;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OAuth2AuthenticationSuccessHandler 테스트")
@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationSuccessHandler(jwtProvider);
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:5173");
    }

    @Test
    @DisplayName("기능_테스트_onAuthenticationSuccess_accessToken과_refreshToken이_포함된_URL로_리다이렉트된다")
    void 기능_테스트_onAuthenticationSuccess_accessToken과_refreshToken이_포함된_URL로_리다이렉트된다()
            throws IOException {
        User user = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("test@example.com")
                .name("테스트유저")
                .role(UserRole.USER)
                .build();
        CustomOAuth2User oAuth2User = new CustomOAuth2User(user, Map.of());
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(jwtProvider.generateAccessToken(any(), any())).thenReturn("test-access-token");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("test-refresh-token");
        when(response.encodeRedirectURL(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(urlCaptor.capture());
        String redirectUrl = urlCaptor.getValue();
        assertThat(redirectUrl).contains("http://localhost:5173/auth/callback");
        assertThat(redirectUrl).contains("accessToken=test-access-token");
        assertThat(redirectUrl).contains("refreshToken=test-refresh-token");
    }
}
