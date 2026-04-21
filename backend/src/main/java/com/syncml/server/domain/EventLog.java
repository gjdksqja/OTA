package com.syncml.server.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_log")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String vin;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;  // SESSION_START, ALERT_RECEIVED, EXEC_SENT, DOWNLOAD_START, etc.

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "raw_xml", columnDefinition = "TEXT")
    private String rawXml;  // SyncML 원본 메시지 (디버깅용)

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

