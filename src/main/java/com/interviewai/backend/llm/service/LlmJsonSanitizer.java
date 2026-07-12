package com.interviewai.backend.llm.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 원본 응답을 Jackson이 파싱 가능한 JSON 문자열로 능동 교정한다.
 *
 * <p>{@link SanitizeDetector}가 "sanitize가 필요했는지" 감지(detect)만 하는 반면,
 * 이 컴포넌트는 실제로 문자열을 변환(transform)한다. 교정 단계:
 * <ol>
 *   <li>markdown 코드블록 fence 제거 ({@code ```json} / bare {@code ```})</li>
 *   <li>첫 {@code {} ~ 마지막 {@code }} 구간만 추출 (앞뒤 산문 방어)</li>
 *   <li>trailing comma 제거 (닫는 {@code }} / {@code ]} 직전 쉼표)</li>
 * </ol>
 * null/empty 입력은 안전하게 처리한다.</p>
 */
@Component
public class LlmJsonSanitizer {

    /** ``` 또는 ```json fence — 언어 태그 포함 제거. */
    private static final Pattern CODEBLOCK_FENCE = Pattern.compile("```[a-zA-Z]*");

    /** JSON trailing comma: 닫는 괄호 직전 쉼표. */
    private static final Pattern TRAILING_COMMA = Pattern.compile(",(\\s*[}\\]])");

    /**
     * 원본 LLM 응답을 파싱 가능한 JSON 문자열로 교정한다.
     *
     * @param raw LLM 원본 응답 (null/empty 허용)
     * @return 교정된 JSON. 입력이 null/empty이면 빈 문자열, {@code {}}를 찾지 못하면 fence만 제거한 결과.
     */
    public String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String result = CODEBLOCK_FENCE.matcher(raw).replaceAll("");

        int jsonStart = result.indexOf('{');
        int jsonEnd = result.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            result = result.substring(jsonStart, jsonEnd + 1);
        }

        Matcher trailingComma = TRAILING_COMMA.matcher(result);
        result = trailingComma.replaceAll("$1");

        return result.trim();
    }
}
