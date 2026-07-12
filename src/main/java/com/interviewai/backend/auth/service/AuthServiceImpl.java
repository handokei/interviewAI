package com.interviewai.backend.auth.service;

import com.interviewai.backend.auth.enums.AuthErrorCode;
import com.interviewai.backend.auth.model.RefreshToken;
import com.interviewai.backend.auth.repository.RefreshTokenRepository;
import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;
import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.global.config.JwtProperties;
import com.interviewai.backend.global.config.jwt.JwtProvider;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserErrorCode;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public AuthTokenServiceDto signup(String email, String password, String confirmPassword, String name) {
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(AuthErrorCode.CONFIRM_PASSWORD_MISMATCH);
        }

        userRepository.findByEmail(email).ifPresent(existing -> {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_REGISTERED);
        });

        User user = userRepository.save(User.builder()
                .provider(OAuthProvider.LOCAL)
                .email(email)
                .name(name)
                .password(passwordEncoder.encode(password))
                .role(UserRole.USER)
                .build());

        return generateTokenPairAndSave(user);
    }

    @Override
    @Transactional
    public AuthTokenServiceDto login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (user.getProvider() != OAuthProvider.LOCAL || user.getPassword() == null) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return generateTokenPairAndSave(user);
    }

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
