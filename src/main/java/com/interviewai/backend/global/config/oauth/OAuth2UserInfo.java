package com.interviewai.backend.global.config.oauth;

public interface OAuth2UserInfo {

    String getProviderId();

    String getEmail();

    String getName();

    String getProfileImageUrl();
}
