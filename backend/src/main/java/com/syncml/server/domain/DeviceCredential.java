package com.syncml.server.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 단말 인증 정보
 *
 * SyncML Cred 요소에서 사용하는 인증 정보 저장.
 * 지원 인증 타입: syncml:auth-basic, syncml:auth-md5, syncml:auth-hmac
 */
@Entity
@Table(name = "device_credential")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCredential {

    @Id
    @Column(length = 50)
    private String vin;

    // 인증 타입: syncml:auth-basic, syncml:auth-md5, syncml:auth-hmac
    @Column(name = "auth_type", length = 50)
    @Builder.Default
    private String authType = "syncml:auth-basic";

    // 사용자 이름 (Base64 디코딩 후 "user:pass" 형태)
    @Column(length = 100)
    private String username;

    // 비밀번호 (해시 저장)
    @Column(name = "password_hash", length = 256)
    private String passwordHash;

    // MD5/HMAC용 nonce (서버가 생성해서 클라이언트에 전달)
    @Column(name = "server_nonce", length = 256)
    private String serverNonce;

    // 마지막 인증 시각
    @Column(name = "last_auth_at")
    private LocalDateTime lastAuthAt;

    // 연속 실패 횟수 (브루트포스 방지)
    @Column(name = "fail_count")
    @Builder.Default
    private Integer failCount = 0;

    // 잠금 여부
    @Column(name = "locked")
    @Builder.Default
    private Boolean locked = false;

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

    public void recordSuccess() {
        this.lastAuthAt = LocalDateTime.now();
        this.failCount = 0;
    }

    public void recordFailure() {
        this.failCount++;
        // 5회 연속 실패 시 잠금
        if (this.failCount >= 5) {
            this.locked = true;
        }
    }

    public void unlock() {
        this.locked = false;
        this.failCount = 0;
    }

    public void updateNonce(String nonce) {
        this.serverNonce = nonce;
    }
}

