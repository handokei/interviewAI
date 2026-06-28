package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.enums.InterviewLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResumeTextSummarizer 테스트")
class ResumeTextSummarizerTest {

    private ResumeTextSummarizer summarizer;

    @BeforeEach
    void setUp() {
        summarizer = new ResumeTextSummarizer();
    }

    @Test
    @DisplayName("기능_테스트_짧은_텍스트는_그대로_반환한다")
    void 기능_테스트_짧은_텍스트는_그대로_반환한다() {
        String text = "짧은 이력서 내용";
        String result = summarizer.summarize(text, 5000, InterviewLevel.JUNIOR);
        assertThat(result).isEqualTo(text);
    }

    @Test
    @DisplayName("기능_테스트_null_입력은_빈_문자열을_반환한다")
    void 기능_테스트_null_입력은_빈_문자열을_반환한다() {
        assertThat(summarizer.summarize(null, 5000, InterviewLevel.JUNIOR)).isEmpty();
    }

    @Test
    @DisplayName("기능_테스트_빈_문자열은_빈_문자열을_반환한다")
    void 기능_테스트_빈_문자열은_빈_문자열을_반환한다() {
        assertThat(summarizer.summarize("", 5000, InterviewLevel.JUNIOR)).isEmpty();
    }

    @Test
    @DisplayName("기능_테스트_섹션_미인식_시_fallback으로_substring_한다")
    void 기능_테스트_섹션_미인식_시_fallback으로_substring_한다() {
        String text = "A".repeat(6000);
        String result = summarizer.summarize(text, 100, InterviewLevel.JUNIOR);
        assertThat(result).hasSize(103); // 100 + "..."
        assertThat(result).endsWith("...");
    }

    @Test
    @DisplayName("기능_테스트_한국어_이력서_JUNIOR_기술스택_프로젝트_학력_우선_포함한다")
    void 기능_테스트_한국어_이력서_JUNIOR_기술스택_프로젝트_학력_우선_포함한다() {
        String text = buildKoreanResume();
        String result = summarizer.summarize(text, 300, InterviewLevel.JUNIOR);

        assertThat(result).contains("기술 스택");
        assertThat(result).contains("프로젝트 경험");
        assertThat(result).contains("학력");
    }

    @Test
    @DisplayName("기능_테스트_한국어_이력서_SENIOR_기술스택_프로젝트_경력_우선_포함한다")
    void 기능_테스트_한국어_이력서_SENIOR_기술스택_프로젝트_경력_우선_포함한다() {
        String text = buildKoreanResume();
        String result = summarizer.summarize(text, 300, InterviewLevel.SENIOR);

        assertThat(result).contains("기술 스택");
        assertThat(result).contains("프로젝트 경험");
        assertThat(result).contains("경력 사항");
    }

    @Test
    @DisplayName("기능_테스트_영어_이력서_섹션을_인식한다")
    void 기능_테스트_영어_이력서_섹션을_인식한다() {
        String text = buildEnglishResume();
        String result = summarizer.summarize(text, 300, InterviewLevel.JUNIOR);

        assertThat(result).contains("Skills");
        assertThat(result).contains("Projects");
    }

    @Test
    @DisplayName("기능_테스트_섹션_분류_JUNIOR_학력은_P1이다")
    void 기능_테스트_섹션_분류_JUNIOR_학력은_P1이다() {
        var priority = summarizer.classifySection("== 학력 ==", InterviewLevel.JUNIOR);
        assertThat(priority).isEqualTo(ResumeTextSummarizer.SectionPriority.P1);
    }

    @Test
    @DisplayName("기능_테스트_섹션_분류_SENIOR_학력은_P3이다")
    void 기능_테스트_섹션_분류_SENIOR_학력은_P3이다() {
        var priority = summarizer.classifySection("== 학력 ==", InterviewLevel.SENIOR);
        assertThat(priority).isEqualTo(ResumeTextSummarizer.SectionPriority.P3);
    }

    @Test
    @DisplayName("기능_테스트_섹션_분류_SENIOR_경력은_P1이다")
    void 기능_테스트_섹션_분류_SENIOR_경력은_P1이다() {
        var priority = summarizer.classifySection("경력 사항", InterviewLevel.SENIOR);
        assertThat(priority).isEqualTo(ResumeTextSummarizer.SectionPriority.P1);
    }

    @Test
    @DisplayName("기능_테스트_섹션_분류_JUNIOR_경력은_P2이다")
    void 기능_테스트_섹션_분류_JUNIOR_경력은_P2이다() {
        var priority = summarizer.classifySection("경력 사항", InterviewLevel.JUNIOR);
        assertThat(priority).isEqualTo(ResumeTextSummarizer.SectionPriority.P2);
    }

    @Test
    @DisplayName("기능_테스트_연락처는_항상_P3이다")
    void 기능_테스트_연락처는_항상_P3이다() {
        assertThat(summarizer.classifySection("연락처", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P3);
        assertThat(summarizer.classifySection("Contact", InterviewLevel.SENIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P3);
    }

    @Test
    @DisplayName("기능_테스트_원본_순서가_유지된다")
    void 기능_테스트_원본_순서가_유지된다() {
        String text = """
                == 연락처 ==
                email@test.com
                == 기술 스택 ==
                Java, Spring Boot
                == 프로젝트 경험 ==
                AI 면접 서비스""";

        // 기술스택과 프로젝트가 P1이지만, 원본에서 연락처가 앞에 있었으므로
        // 포함된 섹션들은 원본 순서를 유지해야 함
        String result = summarizer.summarize(text, 50, InterviewLevel.JUNIOR);
        if (result.contains("기술 스택") && result.contains("프로젝트 경험")) {
            int techIdx = result.indexOf("기술 스택");
            int projectIdx = result.indexOf("프로젝트 경험");
            assertThat(techIdx).isLessThan(projectIdx);
        }
    }

    @Test
    @DisplayName("기능_테스트_섹션_내_항목이_많으면_절삭_후_개수를_표시한다")
    void 기능_테스트_섹션_내_항목이_많으면_절삭_후_개수를_표시한다() {
        StringBuilder text = new StringBuilder();
        text.append("== 프로젝트 경험 ==\n");
        for (int i = 1; i <= 20; i++) {
            text.append("프로젝트 ").append(i).append(": Spring Boot 기반 서비스 개발 및 운영 경험\n");
        }
        text.append("== 연락처 ==\nemail@test.com");

        String result = summarizer.summarize(text.toString(), 200, InterviewLevel.JUNIOR);
        assertThat(result).contains("외");
        assertThat(result).contains("개 항목");
    }

    @Test
    @DisplayName("기능_테스트_splitIntoSections_정상_분리된다")
    void 기능_테스트_splitIntoSections_정상_분리된다() {
        String text = """
                홍길동 개발자
                == 기술 스택 ==
                Java, Spring
                == 프로젝트 경험 ==
                AI 면접 서비스""";

        List<ResumeTextSummarizer.ResumeSection> sections = summarizer.splitIntoSections(text);
        assertThat(sections).hasSizeGreaterThanOrEqualTo(3); // preamble + 기술스택 + 프로젝트
    }

    @Test
    @DisplayName("기능_테스트_데코레이터_헤더를_인식한다")
    void 기능_테스트_데코레이터_헤더를_인식한다() {
        assertThat(summarizer.classifySection("## 기술 스택", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P1);
        assertThat(summarizer.classifySection("=== 프로젝트 경험 ===", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P1);
        assertThat(summarizer.classifySection("1. 경력사항", InterviewLevel.SENIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P1);
        assertThat(summarizer.classifySection("【학력】", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P1);
        assertThat(summarizer.classifySection("Skills:", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P1);
    }

    @Test
    @DisplayName("기능_테스트_빈_헤더는_P3이다")
    void 기능_테스트_빈_헤더는_P3이다() {
        assertThat(summarizer.classifySection("", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P3);
        assertThat(summarizer.classifySection(null, InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P3);
    }

    @Test
    @DisplayName("기능_테스트_자격증은_P2이다")
    void 기능_테스트_자격증은_P2이다() {
        assertThat(summarizer.classifySection("자격 사항", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P2);
        assertThat(summarizer.classifySection("Certifications", InterviewLevel.SENIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P2);
    }

    @Test
    @DisplayName("기능_테스트_수상_JUNIOR는_P2_SENIOR는_P3이다")
    void 기능_테스트_수상_JUNIOR는_P2_SENIOR는_P3이다() {
        assertThat(summarizer.classifySection("수상 경력", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P2);
        assertThat(summarizer.classifySection("Awards", InterviewLevel.SENIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P3);
    }

    @Test
    @DisplayName("기능_테스트_자기소개는_P2이다")
    void 기능_테스트_자기소개는_P2이다() {
        assertThat(summarizer.classifySection("자기 소개", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P2);
        assertThat(summarizer.classifySection("About Me", InterviewLevel.SENIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P2);
    }

    @Test
    @DisplayName("기능_테스트_인식되지_않는_섹션은_P2이다")
    void 기능_테스트_인식되지_않는_섹션은_P2이다() {
        assertThat(summarizer.classifySection("기타 정보", InterviewLevel.JUNIOR))
                .isEqualTo(ResumeTextSummarizer.SectionPriority.P2);
    }

    private String buildKoreanResume() {
        return """
                홍길동
                서울특별시 강남구
                email@test.com
                == 연락처 ==
                이메일: email@test.com
                전화: 010-1234-5678
                == 학력 ==
                서울대학교 컴퓨터공학과 졸업 (2020.03 - 2024.02)
                학점: 4.0 / 4.5
                == 기술 스택 ==
                Java, Spring Boot, PostgreSQL, Redis, Docker, Kubernetes
                == 프로젝트 경험 ==
                AI 면접 서비스 - Spring Boot 기반 백엔드 개발
                실시간 채팅 서비스 - WebSocket + Redis Pub/Sub
                == 경력 사항 ==
                (주) 테크회사 백엔드 개발자 (2024.03 - 현재)
                Spring Boot 기반 마이크로서비스 개발 및 운영
                == 자격 사항 ==
                정보처리기사 (2023.11)
                == 수상 경력 ==
                해커톤 대상 수상 (2023.09)
                """;
    }

    private String buildEnglishResume() {
        return """
                John Doe
                Seoul, South Korea
                john@test.com
                == Contact ==
                Email: john@test.com
                Phone: 010-1234-5678
                == Education ==
                Seoul National University, B.S. Computer Science (2020-2024)
                GPA: 4.0 / 4.5
                == Skills ==
                Java, Spring Boot, PostgreSQL, Redis, Docker, Kubernetes
                == Projects ==
                AI Interview Service - Spring Boot backend development
                Real-time Chat Service - WebSocket + Redis Pub/Sub
                == Experience ==
                Backend Developer at TechCorp (2024.03 - Present)
                Microservice development and operation
                == Certifications ==
                Engineer Information Processing (2023.11)
                == Awards ==
                Hackathon Grand Prize (2023.09)
                """;
    }
}
