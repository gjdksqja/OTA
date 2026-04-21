package com.syncml.server.config;

import com.syncml.server.domain.Device;
import com.syncml.server.domain.DeviceCredential;
import com.syncml.server.domain.DeviceStatus;
import com.syncml.server.repository.DeviceCredentialRepository;
import com.syncml.server.repository.DeviceRepository;
import com.syncml.server.syncml.service.SyncMLAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")  // 개발 환경에서만 실행
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final DeviceRepository deviceRepository;
    private final DeviceCredentialRepository credentialRepository;
    private final SyncMLAuthService authService;

    @Override
    public void run(String... args) {
        // 초기 테스트 단말 데이터
        createDeviceIfNotExists("VIN-0001", "TEST-MODEL-A", "1.0.0");
        createDeviceIfNotExists("VIN-0002", "TEST-MODEL-B", "1.0.0");
        createDeviceIfNotExists("VIN-0003", "TEST-MODEL-C", "1.1.0");

        // 인증 정보 생성 (테스트용)
        createCredentialIfNotExists("VIN-0001", "device0001", "password123");
        createCredentialIfNotExists("VIN-0002", "device0002", "password123");
        createCredentialIfNotExists("VIN-0003", "device0003", "password123");

        log.info("=== 초기 데이터 로드 완료 ===");
        log.info("등록된 단말 수: {}", deviceRepository.count());
        log.info("등록된 인증 정보 수: {}", credentialRepository.count());
    }

    private void createDeviceIfNotExists(String vin, String model, String version) {
        if (deviceRepository.findById(vin).isEmpty()) {
            Device device = Device.builder()
                    .vin(vin)
                    .model(model)
                    .currentVersion(version)
                    .deviceStatus(DeviceStatus.IDLE)
                    .build();
            deviceRepository.save(device);
            log.info("단말 생성: {} ({})", vin, model);
        }
    }

    private void createCredentialIfNotExists(String vin, String username, String password) {
        if (credentialRepository.findById(vin).isEmpty()) {
            authService.registerCredential(vin, username, password, "syncml:auth-basic");
            log.info("인증 정보 생성: {} ({})", vin, username);
        }
    }
}

