package com.syncml.server.repository;

import com.syncml.server.domain.Device;
import com.syncml.server.domain.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, String> {

    List<Device> findByDeviceStatus(DeviceStatus status);

    // Heartbeat 갱신
    @Modifying
    @Query("UPDATE Device d SET d.lastSeenAt = :now WHERE d.vin = :vin")
    void updateLastSeenAt(@Param("vin") String vin, @Param("now") LocalDateTime now);

    // 오프라인 단말 조회 (마지막 접속 후 일정 시간 경과)
    @Query("SELECT d FROM Device d WHERE d.lastSeenAt < :threshold")
    List<Device> findOfflineDevices(@Param("threshold") LocalDateTime threshold);
}

