package com.interviewai.backend.user.service;

import com.interviewai.backend.user.controller.dto.UserProfileResponseDto;

public interface UserService {

    UserProfileResponseDto findUserProfile(Long userId);

    void changePassword(Long userId, String currentPassword, String newPassword, String confirmNewPassword);
}
