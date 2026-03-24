package com.interviewai.backend.interview.model;

import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import com.interviewai.backend.interview.enums.InterviewStatus;
import com.interviewai.backend.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status;

    private String jobTitle;

    private String jobPostingUrl;

    @Column(columnDefinition = "TEXT")
    private String jobPostingContent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public InterviewSession(User user, InterviewMode mode, InterviewLevel level,
                            String jobTitle, String jobPostingUrl, String jobPostingContent) {
        this.user = user;
        this.mode = mode;
        this.level = level;
        this.status = InterviewStatus.IN_PROGRESS;
        this.jobTitle = jobTitle;
        this.jobPostingUrl = jobPostingUrl;
        this.jobPostingContent = jobPostingContent;
    }

    public void complete() {
        this.status = InterviewStatus.COMPLETED;
    }

    public void cancel() {
        this.status = InterviewStatus.CANCELLED;
    }
}
