package com.interviewai.backend.config.oauth;

public interface OAuth2UserInfo {

    String getProviderId();

    String getEmail();

    String getName();

    String getProfileImageUrl();
}
