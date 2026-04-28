package com.interviewai.backend.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EncryptedStringConverter 테스트")
class EncryptedStringConverterTest {

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter("test-encrypt-key!");
    }

    @Test
    @DisplayName("기능_테스트_문자열을_암호화하고_복호화하면_원본과_동일하다")
    void 기능_테스트_문자열을_암호화하고_복호화하면_원본과_동일하다() {
        String original = "ghp_test_token_12345";

        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
        assertThat(encrypted).isNotEqualTo(original);
    }

    @Test
    @DisplayName("기능_테스트_null_입력은_null을_반환한다_암호화")
    void 기능_테스트_null_입력은_null을_반환한다_암호화() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("기능_테스트_null_입력은_null을_반환한다_복호화")
    void 기능_테스트_null_입력은_null을_반환한다_복호화() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("기능_테스트_같은_문자열을_두번_암호화하면_다른_결과가_나온다")
    void 기능_테스트_같은_문자열을_두번_암호화하면_다른_결과가_나온다() {
        String original = "test_token";

        String encrypted1 = converter.convertToDatabaseColumn(original);
        String encrypted2 = converter.convertToDatabaseColumn(original);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("기능_테스트_긴_토큰도_정상_암복호화된다")
    void 기능_테스트_긴_토큰도_정상_암복호화된다() {
        String longToken = "github_pat_" + "A".repeat(200);

        String encrypted = converter.convertToDatabaseColumn(longToken);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(longToken);
    }

    @Test
    @DisplayName("기능_테스트_짧은_키로도_정상_동작한다")
    void 기능_테스트_짧은_키로도_정상_동작한다() {
        EncryptedStringConverter shortKeyConverter = new EncryptedStringConverter("short");

        String original = "test_token";
        String encrypted = shortKeyConverter.convertToDatabaseColumn(original);
        String decrypted = shortKeyConverter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }
}
