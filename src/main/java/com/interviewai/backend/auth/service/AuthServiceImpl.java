package com.interviewai.backend.auth.service;

import com.interviewai.backend.auth.enums.AuthErrorCode;
import com.interviewai.backend.auth.model.RefreshToken;
import com.interviewai.backend.auth.repository.RefreshTokenRepository;
import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;
import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.global.config.JwtProperties;
import com.interviewai.backend.global.config.jwt.JwtProvider;
import com.interviewai.backend.user.enums.UserErrorCode;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthTokenServiceDto refreshToken(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken).orElse(null);
        if (storedToken == null) {
            refreshTokenRepository.deleteAllByUserId(userId);
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        refreshTokenRepository.deleteByToken(storedToken.getToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return generateTokenPairAndSave(user);
    }

    private AuthTokenServiceDto generateTokenPairAndSave(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration() / 1000))
                .build());

        return AuthTokenServiceDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
