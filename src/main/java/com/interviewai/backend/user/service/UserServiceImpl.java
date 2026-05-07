package com.interviewai.backend.user.service;

import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.user.controller.dto.UserProfileResponseDto;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserErrorCode;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserProfileResponseDto findUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return UserProfileResponseDto.from(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword, String confirmNewPassword) {
        if (!newPassword.equals(confirmNewPassword)) {
            throw new BusinessException(UserErrorCode.CONFIRM_PASSWORD_MISMATCH);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (user.getProvider() != OAuthProvider.LOCAL || user.getPassword() == null) {
            throw new BusinessException(UserErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED);
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        if (currentPassword.equals(newPassword)) {
            throw new BusinessException(UserErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
    }
}
