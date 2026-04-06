package com.interviewai.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CorsProperties 테스트")
class CorsPropertiesTest {

    @Test
    @DisplayName("기능_테스트_기본값으로_CorsProperties가_초기화된다")
    void 기능_테스트_기본값으로_CorsProperties가_초기화된다() {
        CorsProperties properties = new CorsProperties();

        assertThat(properties.getAllowedOrigins()).containsExactly(
                "http://localhost:5173",
                "http://localhost:3000",
                "https://*.cloudfront.net"
        );
    }

    @Test
    @DisplayName("기능_테스트_허용_origins를_변경할_수_있다")
    void 기능_테스트_허용_origins를_변경할_수_있다() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("https://myapp.com"));

        assertThat(properties.getAllowedOrigins()).containsExactly("https://myapp.com");
    }
}
