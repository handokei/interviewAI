package com.interviewai.backend.global.config;

import com.interviewai.backend.global.config.jwt.JwtProvider;
import com.interviewai.backend.global.config.oauth.CustomOAuth2UserService;
import com.interviewai.backend.global.config.oauth.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityConfig 테스트")
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Mock
    private JwtProvider jwtProvider;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        CorsProperties corsProperties = new CorsProperties();
        securityConfig = new SecurityConfig(
                customOAuth2UserService,
                oAuth2AuthenticationSuccessHandler,
                jwtProvider,
                corsProperties
        );
    }

    @Test
    @DisplayName("기능_테스트_corsConfigurationSource_허용된_origin이_설정된다")
    void 기능_테스트_corsConfigurationSource_허용된_origin이_설정된다() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfiguration(mockRequest("/api/test"));
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns())
                .contains("http://localhost:5173", "http://localhost:3000", "https://*.cloudfront.net");
    }

    @Test
    @DisplayName("기능_테스트_corsConfigurationSource_허용된_HTTP_메서드가_설정된다")
    void 기능_테스트_corsConfigurationSource_허용된_HTTP_메서드가_설정된다() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfiguration(mockRequest("/api/test"));
        assertThat(config.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    }

    @Test
    @DisplayName("기능_테스트_corsConfigurationSource_credentials_허용이_설정된다")
    void 기능_테스트_corsConfigurationSource_credentials_허용이_설정된다() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfiguration(mockRequest("/api/test"));
        assertThat(config.getAllowCredentials()).isTrue();
    }

    private org.springframework.mock.web.MockHttpServletRequest mockRequest(String path) {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
