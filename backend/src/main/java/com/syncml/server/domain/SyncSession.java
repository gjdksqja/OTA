package com.syncml.server.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * SyncML 세션 관리 엔티티
 *
 * SyncML은 HTTP 위에서 여러 메시지를 주고받는 "세션" 개념이 있음.
 * SessionID로 연속성 보장, MsgID/CmdID로 메시지 추적.
 */
@Entity
@Table(name = "sync_session")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncSession {

    @Id
    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(length = 50, nullable = false)
    private String vin;

    // 현재 FUMO 흐름 단계 (1~6)
    @Column(name = "current_step")
    @Builder.Default
    private Integer currentStep = 0;

    // 마지막 메시지 ID (클라이언트 → 서버)
    @Column(name = "last_msg_id")
    @Builder.Default
    private Integer lastMsgId = 0;

    // 마지막 명령 ID (서버 → 클라이언트)
    @Column(name = "last_cmd_id")
    @Builder.Default
    private Integer lastCmdId = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", length = 30)
    @Builder.Default
    private SyncSessionStatus sessionStatus = SyncSessionStatus.INITIALIZED;

    // 인증 완료 여부
    @Column(name = "authenticated")
    @Builder.Default
    private Boolean authenticated = false;

    // 현재 진행 중인 Job ID (있으면)
    @Column(name = "current_job_id")
    private Long currentJobId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
        lastActivityAt = LocalDateTime.now();
    }

    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public int nextCmdId() {
        return ++this.lastCmdId;
    }

    public void advanceStep() {
        this.currentStep++;
        updateActivity();
    }

    public boolean isExpired(int timeoutMinutes) {
        if (lastActivityAt == null) return true;
        return lastActivityAt.plusMinutes(timeoutMinutes).isBefore(LocalDateTime.now());
    }

    public void complete() {
        this.sessionStatus = SyncSessionStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.sessionStatus = SyncSessionStatus.FAILED;
        this.endedAt = LocalDateTime.now();
    }
}

