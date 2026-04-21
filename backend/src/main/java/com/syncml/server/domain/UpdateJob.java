package com.syncml.server.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "update_job")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "target_vin", length = 50, nullable = false)
    private String targetVin;

    @Column(name = "command_type", length = 50, nullable = false)
    private String commandType;  // FUMO_UPDATE, SCOMO_INSTALL 등

    @Column(name = "payload_version", length = 50)
    private String payloadVersion;

    @Column(name = "pkg_url", length = 500)
    private String pkgUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 재시도 가능 여부 (최대 3회)
    public boolean canRetry() {
        return retryCount < 3;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}

