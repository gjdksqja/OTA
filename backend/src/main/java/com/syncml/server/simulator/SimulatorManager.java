package com.syncml.server.simulator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 단말 시뮬레이터 매니저
 *
 * dev 프로파일에서 3개의 가상 단말을 실행.
 * 각 단말은 별도 스레드에서 SyncML 서버와 통신.
 */
@Component
@Profile("dev")
@Slf4j
public class SimulatorManager implements CommandLineRunner {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${simulator.enabled:true}")
    private boolean simulatorEnabled;

    @Value("${simulator.start-delay-ms:5000}")
    private int startDelayMs;

    private final List<DeviceSimulator> simulators = new ArrayList<>();
    private ExecutorService executorService;

    // 시뮬레이터 설정 (VIN, 사용자, 비밀번호)
    private static final String[][] DEVICE_CONFIGS = {
        {"VIN-0001", "device0001", "password123"},
        {"VIN-0002", "device0002", "password123"},
        {"VIN-0003", "device0003", "password123"}
    };

    @Override
    public void run(String... args) throws Exception {
        if (!simulatorEnabled) {
            log.info("시뮬레이터가 비활성화되어 있습니다 (simulator.enabled=false)");
            return;
        }

        log.info("=== 단말 시뮬레이터 매니저 시작 ===");
        log.info("서버 시작 대기 중... ({}ms)", startDelayMs);

        // 서버가 완전히 시작될 때까지 대기
        Thread.sleep(startDelayMs);

        String serverUrl = "http://localhost:" + serverPort;
        executorService = Executors.newFixedThreadPool(DEVICE_CONFIGS.length);

        for (String[] config : DEVICE_CONFIGS) {
            String vin = config[0];
            String username = config[1];
            String password = config[2];

            DeviceSimulator simulator = new DeviceSimulator(vin, username, password, serverUrl);
            simulators.add(simulator);
            executorService.submit(simulator);

            log.info("시뮬레이터 시작: {} (user: {})", vin, username);

            // 시뮬레이터 간 시작 시간 분산 (1초 간격)
            Thread.sleep(1000);
        }

        log.info("=== {} 개의 시뮬레이터 실행 중 ===", simulators.size());
    }

    @PreDestroy
    public void shutdown() {
        log.info("시뮬레이터 종료 중...");

        // 모든 시뮬레이터 중지
        for (DeviceSimulator simulator : simulators) {
            simulator.stop();
        }

        // ExecutorService 종료
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("시뮬레이터 종료 완료");
    }
}

