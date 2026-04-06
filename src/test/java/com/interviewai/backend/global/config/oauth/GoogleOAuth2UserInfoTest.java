package com.interviewai.backend.global.config.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoogleOAuth2UserInfo 테스트")
class GoogleOAuth2UserInfoTest {

    @Test
    @DisplayName("기능_테스트_getProviderId_sub_값이_반환된다")
    void 기능_테스트_getProviderId_sub_값이_반환된다() {
        GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(
                Map.of("sub", "google-user-id-123"));

        assertThat(userInfo.getProviderId()).isEqualTo("google-user-id-123");
    }

    @Test
    @DisplayName("기능_테스트_getEmail_email_값이_반환된다")
    void 기능_테스트_getEmail_email_값이_반환된다() {
        GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(
                Map.of("sub", "123", "email", "google@example.com"));

        assertThat(userInfo.getEmail()).isEqualTo("google@example.com");
    }

    @Test
    @DisplayName("기능_테스트_getName_name_값이_반환된다")
    void 기능_테스트_getName_name_값이_반환된다() {
        GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(
                Map.of("sub", "123", "name", "김구글"));

        assertThat(userInfo.getName()).isEqualTo("김구글");
    }

    @Test
    @DisplayName("기능_테스트_getProfileImageUrl_picture_값이_반환된다")
    void 기능_테스트_getProfileImageUrl_picture_값이_반환된다() {
        GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(
                Map.of("sub", "123", "picture", "https://lh3.googleusercontent.com/photo.jpg"));

        assertThat(userInfo.getProfileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/photo.jpg");
    }
}
