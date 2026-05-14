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

    // ===== 패키지 메타 (파일 사이징 + 무결성, 추후 PKI 검증과 연계) =====

    /** 패키지 파일 크기 (byte). MoreData 청킹 / 진행률 계산에 사용 */
    @Column(name = "pkg_size")
    private Long pkgSize;

    /** 패키지 SHA-256 hex (./FUMO/PackageHash 로 발행) */
    @Column(name = "pkg_sha256", length = 128)
    private String pkgSha256;

    /** 패키지 서명 (Base64). PKI 검증 흐름에서 사용 (추후 구현) */
    @Column(name = "pkg_signature", columnDefinition = "TEXT")
    private String pkgSignature;

    /** 서명 알고리즘 (예: SHA256withRSA) */
    @Column(name = "signature_algorithm", length = 50)
    private String signatureAlgorithm;

    /** 서명 인증서 체인 (PEM, 추후 PKI 검증) */
    @Column(name = "signing_cert_chain", columnDefinition = "TEXT")
    private String signingCertChain;

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

