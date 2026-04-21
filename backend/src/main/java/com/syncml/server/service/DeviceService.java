package com.syncml.server.service;

import com.syncml.server.domain.*;
import com.syncml.server.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UpdateJobRepository updateJobRepository;
    private final EventLogService eventLogService;

    // 단말 등록
    public Device registerDevice(String vin, String model, String currentVersion) {
        Device device = Device.builder()
                .vin(vin)
                .model(model)
                .currentVersion(currentVersion)
                .deviceStatus(DeviceStatus.IDLE)
                .build();

        Device saved = deviceRepository.save(device);
        eventLogService.log(vin, null, null, "DEVICE_REGISTERED", "단말 등록: " + model);
        return saved;
    }

    // 모든 단말 조회
    @Transactional(readOnly = true)
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    // 단말 조회
    @Transactional(readOnly = true)
    public Optional<Device> getDevice(String vin) {
        return deviceRepository.findById(vin);
    }

    // Heartbeat 갱신 (10초 주기)
    public void updateHeartbeat(String vin) {
        deviceRepository.findById(vin).ifPresent(device -> {
            device.updateHeartbeat();
            deviceRepository.save(device);
        });
    }

    // 단말 상태 변경
    public void updateDeviceStatus(String vin, DeviceStatus status) {
        deviceRepository.findById(vin).ifPresent(device -> {
            device.setDeviceStatus(status);
            deviceRepository.save(device);
            log.info("Device {} status changed to {}", vin, status);
        });
    }

    // 단말 버전 업데이트
    public void updateDeviceVersion(String vin, String newVersion) {
        deviceRepository.findById(vin).ifPresent(device -> {
            device.setCurrentVersion(newVersion);
            device.setTargetVersion(null);
            device.setDeviceStatus(DeviceStatus.IDLE);
            deviceRepository.save(device);
            eventLogService.log(vin, null, null, "VERSION_UPDATED", "버전 업데이트 완료: " + newVersion);
        });
    }

    // 오프라인 단말 조회 (60초 이상 미접속)
    @Transactional(readOnly = true)
    public List<Device> getOfflineDevices() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(60);
        return deviceRepository.findOfflineDevices(threshold);
    }
}

