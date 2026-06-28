package com.interviewai.backend.llm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SanitizeDetector 테스트")
class SanitizeDetectorTest {

    private SanitizeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new SanitizeDetector();
    }

    @Test
    @DisplayName("기능_테스트_markdown_코드블록_fence가_있으면_true_를_반환한다")
    void 기능_테스트_markdown_코드블록_fence가_있으면_true_를_반환한다() {
        String response = "```json\n{\"suggestFinish\": false}\n```";
        assertThat(detector.isSanitizeRequired(response)).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_JSON_trailing_comma가_있으면_true_를_반환한다")
    void 기능_테스트_JSON_trailing_comma가_있으면_true_를_반환한다() {
        String response = "{ \"a\": 1, \"b\": 2, }";
        assertThat(detector.isSanitizeRequired(response)).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_배열_trailing_comma도_감지된다")
    void 기능_테스트_배열_trailing_comma도_감지된다() {
        String response = "[1, 2, 3, ]";
        assertThat(detector.isSanitizeRequired(response)).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_깨끗한_JSON은_false를_반환한다")
    void 기능_테스트_깨끗한_JSON은_false를_반환한다() {
        String response = "{ \"suggestFinish\": false, \"answerLevel\": \"PASS\" }";
        assertThat(detector.isSanitizeRequired(response)).isFalse();
    }

    @Test
    @DisplayName("예외_테스트_null_응답은_false를_반환한다")
    void 예외_테스트_null_응답은_false를_반환한다() {
        assertThat(detector.isSanitizeRequired(null)).isFalse();
    }

    @Test
    @DisplayName("예외_테스트_빈_문자열은_false를_반환한다")
    void 예외_테스트_빈_문자열은_false를_반환한다() {
        assertThat(detector.isSanitizeRequired("")).isFalse();
    }
}
