package com.interviewai.backend.user.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangePasswordRequestDto {

    @NotBlank(message = "현재 비밀번호는 필수입니다.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*]{8,20}$",
            message = "비밀번호는 영문과 숫자를 포함해야 합니다.")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
    private String confirmNewPassword;
}
