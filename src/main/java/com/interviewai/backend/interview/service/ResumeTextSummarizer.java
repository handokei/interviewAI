package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.enums.InterviewLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResumeTextSummarizer {

    enum SectionPriority { P1, P2, P3 }

    record ResumeSection(int index, String header, String body, SectionPriority priority) {
        ResumeSection withPriority(SectionPriority newPriority) {
            return new ResumeSection(index, header, body, newPriority);
        }

        int length() {
            return header.length() + body.length();
        }
    }

    private static final Pattern SECTION_HEADER = Pattern.compile(
            "^[\\s#=\\-*|【]{0,20}(?:\\d+\\.?\\s{0,5})?"
                    + "(?:기술\\s{0,5}스택|skills?|tech(?:nical)?\\s{0,5}(?:stack|skills?)"
                    + "|프로젝트\\s{0,5}(?:경험|이력)?|projects?"
                    + "|경력\\s{0,5}(?:사항|기술서)?|work\\s{0,5}experience|experience|career"
                    + "|자격\\s{0,5}(?:증|사항)|certifications?|licenses?"
                    + "|수상\\s{0,5}(?:경력|내역)?|awards?|honors?"
                    + "|자기\\s{0,5}소개|summary|objective|about\\s{0,5}me|소개"
                    + "|학력\\s{0,5}(?:사항)?|education|academic"
                    + "|연락처|contact|인적\\s{0,5}사항|personal\\s{0,5}info)"
                    + "[\\s#=\\-*|】:]{0,20}$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE
    );

    private static final Pattern SKILLS_PATTERN = Pattern.compile(
            "기술\\s*스택|skills?|tech(?:nical)?\\s*(?:stack|skills?)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PROJECTS_PATTERN = Pattern.compile(
            "프로젝트|projects?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EXPERIENCE_PATTERN = Pattern.compile(
            "경력|work\\s*experience|experience|career", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EDUCATION_PATTERN = Pattern.compile(
            "학력|education|academic", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CERTIFICATIONS_PATTERN = Pattern.compile(
            "자격|certifications?|licenses?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AWARDS_PATTERN = Pattern.compile(
            "수상|awards?|honors?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "자기\\s*소개|summary|objective|about\\s*me|소개", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CONTACT_PATTERN = Pattern.compile(
            "연락처|contact|인적\\s*사항|personal\\s*info", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public String summarize(String parsedText, int maxLength, InterviewLevel level) {
        if (parsedText == null || parsedText.isEmpty()) {
            return "";
        }
        if (parsedText.length() <= maxLength) {
            return parsedText;
        }

        List<ResumeSection> sections = splitIntoSections(parsedText);

        if (sections.size() < 2) {
            return parsedText.substring(0, maxLength) + "...";
        }

        List<ResumeSection> classified = sections.stream()
                .map(s -> s.withPriority(classifySection(s.header(), level)))
                .toList();

        return assembleWithBudget(classified, maxLength);
    }

    List<ResumeSection> splitIntoSections(String text) {
        List<ResumeSection> sections = new ArrayList<>();
        Matcher matcher = SECTION_HEADER.matcher(text);

        List<int[]> headerPositions = new ArrayList<>();
        while (matcher.find()) {
            headerPositions.add(new int[]{matcher.start(), matcher.end()});
        }

        if (headerPositions.isEmpty()) {
            sections.add(new ResumeSection(0, "", text, SectionPriority.P2));
            return sections;
        }

        if (headerPositions.get(0)[0] > 0) {
            String preamble = text.substring(0, headerPositions.get(0)[0]).trim();
            if (!preamble.isEmpty()) {
                sections.add(new ResumeSection(0, "", preamble, SectionPriority.P3));
            }
        }

        for (int i = 0; i < headerPositions.size(); i++) {
            String header = text.substring(headerPositions.get(i)[0], headerPositions.get(i)[1]).trim();
            int bodyStart = headerPositions.get(i)[1];
            int bodyEnd = (i + 1 < headerPositions.size()) ? headerPositions.get(i + 1)[0] : text.length();
            String body = text.substring(bodyStart, bodyEnd).trim();
            sections.add(new ResumeSection(sections.size(), header, body, SectionPriority.P2));
        }

        return sections;
    }

    SectionPriority classifySection(String headerLine, InterviewLevel level) {
        if (headerLine == null || headerLine.isEmpty()) {
            return SectionPriority.P3;
        }

        if (SKILLS_PATTERN.matcher(headerLine).find() || PROJECTS_PATTERN.matcher(headerLine).find()) {
            return SectionPriority.P1;
        }

        if (EXPERIENCE_PATTERN.matcher(headerLine).find()) {
            return level == InterviewLevel.SENIOR ? SectionPriority.P1 : SectionPriority.P2;
        }

        if (EDUCATION_PATTERN.matcher(headerLine).find()) {
            return level == InterviewLevel.JUNIOR ? SectionPriority.P1 : SectionPriority.P3;
        }

        if (CERTIFICATIONS_PATTERN.matcher(headerLine).find()) {
            return SectionPriority.P2;
        }

        if (AWARDS_PATTERN.matcher(headerLine).find()) {
            return level == InterviewLevel.JUNIOR ? SectionPriority.P2 : SectionPriority.P3;
        }

        if (SUMMARY_PATTERN.matcher(headerLine).find()) {
            return SectionPriority.P2;
        }

        if (CONTACT_PATTERN.matcher(headerLine).find()) {
            return SectionPriority.P3;
        }

        return SectionPriority.P2;
    }

    String assembleWithBudget(List<ResumeSection> sections, int maxLength) {
        List<ResumeSection> p1 = sections.stream().filter(s -> s.priority() == SectionPriority.P1).toList();
        List<ResumeSection> p2 = sections.stream().filter(s -> s.priority() == SectionPriority.P2).toList();
        List<ResumeSection> p3 = sections.stream().filter(s -> s.priority() == SectionPriority.P3).toList();

        List<ResumeSection> included = new ArrayList<>();
        int remainingBudget = maxLength;

        remainingBudget = allocateSections(p1, included, remainingBudget);
        remainingBudget = allocateSections(p2, included, remainingBudget);
        allocateSections(p3, included, remainingBudget);

        included.sort(Comparator.comparingInt(ResumeSection::index));

        StringBuilder result = new StringBuilder();
        for (ResumeSection section : included) {
            if (!result.isEmpty()) {
                result.append("\n");
            }
            if (!section.header().isEmpty()) {
                result.append(section.header()).append("\n");
            }
            result.append(section.body());
        }

        return result.toString();
    }

    private int allocateSections(List<ResumeSection> sections, List<ResumeSection> included, int remainingBudget) {
        for (ResumeSection section : sections) {
            if (remainingBudget <= 0) break;

            int sectionLength = section.length();
            if (sectionLength <= remainingBudget) {
                included.add(section);
                remainingBudget -= sectionLength;
            } else {
                String truncatedBody = truncateSectionBody(section.body(), remainingBudget - section.header().length());
                included.add(new ResumeSection(section.index(), section.header(), truncatedBody, section.priority()));
                remainingBudget = 0;
            }
        }
        return remainingBudget;
    }

    private String truncateSectionBody(String body, int budget) {
        if (budget <= 0) return "";

        String[] lines = body.split("\n");
        if (lines.length <= 1) {
            return body.substring(0, Math.min(body.length(), budget)) + "...";
        }

        StringBuilder result = new StringBuilder();
        int includedCount = 0;

        for (String line : lines) {
            if (result.length() + line.length() + 1 > budget) break;
            if (!result.isEmpty()) result.append("\n");
            result.append(line);
            includedCount++;
        }

        int remaining = lines.length - includedCount;
        if (remaining > 0) {
            result.append("\n... 외 ").append(remaining).append("개 항목");
        }

        return result.toString();
    }
}
