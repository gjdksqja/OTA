package com.syncml.server.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @Column(length = 50)
    private String vin;

    @Column(length = 100)
    private String model;

    @Column(name = "current_version", length = 50)
    private String currentVersion;

    @Column(name = "target_version", length = 50)
    private String targetVersion;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_status", length = 20)
    @Builder.Default
    private DeviceStatus deviceStatus = DeviceStatus.IDLE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastSeenAt = LocalDateTime.now();
    }

    public void updateHeartbeat() {
        this.lastSeenAt = LocalDateTime.now();
    }
}

