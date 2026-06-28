package com.interviewai.backend.llm.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * LLM 응답에 sanitize 휴리스틱이 필요했는지 감지한다.
 *
 * <p>현재 detect 패턴:
 * <ul>
 *   <li>markdown 코드블록 fence ({@code ```} 시작/종료)</li>
 *   <li>JSON trailing comma (예: {@code , ]} / {@code , }})</li>
 * </ul>
 * 둘 중 하나라도 매치되면 {@code true} — metric counter가 1 증가.</p>
 */
@Component
public class SanitizeDetector {

    /** ``` 또는 ```json fence 검출. */
    private static final Pattern CODEBLOCK_FENCE = Pattern.compile("```");

    /** JSON trailing comma: 닫는 괄호 직전 쉼표. */
    private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*[}\\]]");

    /**
     * sanitize 적용이 필요한 패턴이 응답에 있는지 검사한다.
     *
     * @param response LLM 원본 응답 (null/empty 허용 — 둘 다 false)
     */
    public boolean isSanitizeRequired(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        return CODEBLOCK_FENCE.matcher(response).find()
                || TRAILING_COMMA.matcher(response).find();
    }
}
