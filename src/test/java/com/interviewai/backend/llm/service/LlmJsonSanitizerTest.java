package com.interviewai.backend.llm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmJsonSanitizer 테스트")
class LlmJsonSanitizerTest {

    private LlmJsonSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new LlmJsonSanitizer();
    }

    @Test
    @DisplayName("기능_테스트_json_언어태그_fence를_제거한다")
    void 기능_테스트_json_언어태그_fence를_제거한다() {
        String raw = "```json\n{\"a\": 1}\n```";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"a\": 1}");
    }

    @Test
    @DisplayName("기능_테스트_언어태그_없는_bare_fence를_제거한다")
    void 기능_테스트_언어태그_없는_bare_fence를_제거한다() {
        String raw = "```\n{\"a\": 1}\n```";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"a\": 1}");
    }

    @Test
    @DisplayName("기능_테스트_객체_닫는괄호_직전_trailing_comma를_제거한다")
    void 기능_테스트_객체_닫는괄호_직전_trailing_comma를_제거한다() {
        String raw = "{\"a\": 1, \"b\": 2,}";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"a\": 1, \"b\": 2}");
    }

    @Test
    @DisplayName("기능_테스트_배열_닫는괄호_직전_trailing_comma를_제거한다")
    void 기능_테스트_배열_닫는괄호_직전_trailing_comma를_제거한다() {
        String raw = "{\"a\": [1, 2, 3,]}";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"a\": [1, 2, 3]}");
    }

    @Test
    @DisplayName("기능_테스트_앞뒤_산문에서_JSON_구간만_추출한다")
    void 기능_테스트_앞뒤_산문에서_JSON_구간만_추출한다() {
        String raw = "다음은 평가 결과입니다: {\"a\": 1} 이상입니다.";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"a\": 1}");
    }

    @Test
    @DisplayName("기능_테스트_이미_깨끗한_JSON은_그대로_반환한다")
    void 기능_테스트_이미_깨끗한_JSON은_그대로_반환한다() {
        String raw = "{\"a\": 1, \"b\": 2}";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"a\": 1, \"b\": 2}");
    }

    @Test
    @DisplayName("기능_테스트_fence와_trailing_comma가_함께_있어도_모두_교정한다")
    void 기능_테스트_fence와_trailing_comma가_함께_있어도_모두_교정한다() {
        String raw = "```json\n{\"a\": [1, 2,], \"b\": 3,}\n```";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"a\": [1, 2], \"b\": 3}");
    }

    @Test
    @DisplayName("예외_테스트_null_입력은_빈_문자열을_반환한다")
    void 예외_테스트_null_입력은_빈_문자열을_반환한다() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
    }

    @Test
    @DisplayName("예외_테스트_빈_문자열_입력은_빈_문자열을_반환한다")
    void 예외_테스트_빈_문자열_입력은_빈_문자열을_반환한다() {
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    @DisplayName("예외_테스트_중괄호가_없는_문자열은_fence만_제거한_텍스트를_반환한다")
    void 예외_테스트_중괄호가_없는_문자열은_fence만_제거한_텍스트를_반환한다() {
        String raw = "```\n파싱 불가 텍스트\n```";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("파싱 불가 텍스트");
    }

    @Test
    @DisplayName("예외_테스트_여는괄호만_있고_닫는괄호가_없으면_구간추출_없이_원본을_반환한다")
    void 예외_테스트_여는괄호만_있고_닫는괄호가_없으면_구간추출_없이_원본을_반환한다() {
        // '{'는 있으나 뒤에 '}'가 없어 jsonEnd <= jsonStart → substring 미적용
        String raw = "{\"a\": 1";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("{\"a\": 1");
    }
}
