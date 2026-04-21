package com.syncml.server.repository;

import com.syncml.server.domain.DeviceCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, String> {

    // 잠긴 계정 목록
    List<DeviceCredential> findByLockedTrue();

    // 특정 인증 타입 사용 단말
    List<DeviceCredential> findByAuthType(String authType);
}

