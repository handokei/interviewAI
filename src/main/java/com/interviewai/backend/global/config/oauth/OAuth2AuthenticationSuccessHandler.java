package com.interviewai.backend.global.config.oauth;

import com.interviewai.backend.auth.model.RefreshToken;
import com.interviewai.backend.auth.repository.RefreshTokenRepository;
import com.interviewai.backend.global.config.JwtProperties;
import com.interviewai.backend.global.config.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String accessToken = jwtProvider.generateAccessToken(oAuth2User.getUserId(), oAuth2User.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(oAuth2User.getUserId());

        refreshTokenRepository.deleteAllByUserId(oAuth2User.getUserId());
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(oAuth2User.getUserId())
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration() / 1000))
                .build());

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/callback")
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        log.info("OAuth2 login success for user: {}", oAuth2User.getUserId());
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
