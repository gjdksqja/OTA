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

    // ===== DevInfo 사전 교환으로 채워지는 필드 (./DevInfo/*) =====

    /** ./DevInfo/Ext/MaxMsgSize - 단말이 1회 수신 가능한 최대 SyncML 메시지 byte 크기 */
    @Column(name = "max_msg_size")
    private Long maxMsgSize;

    /** ./DevInfo/Ext/MaxObjSize - 단말이 처리 가능한 최대 단일 오브젝트 byte 크기 */
    @Column(name = "max_obj_size")
    private Long maxObjSize;

    /** Large Object (MoreData 청킹) 지원 여부 */
    @Column(name = "support_large_obj")
    @Builder.Default
    private Boolean supportLargeObj = Boolean.FALSE;

    /** ./DevInfo/Man - 제조사 */
    @Column(length = 100)
    private String manufacturer;

    /** ./DevInfo/DmV - DM Client 버전 */
    @Column(name = "dm_client_version", length = 50)
    private String dmClientVersion;

    /** ./DevInfo/Lang - 단말 언어 */
    @Column(length = 20)
    private String lang;

    /** ./DevInfo/DevId - 단말 자기 식별자 (보통 IMEI/UUID) */
    @Column(name = "dev_id", length = 100)
    private String devId;

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

