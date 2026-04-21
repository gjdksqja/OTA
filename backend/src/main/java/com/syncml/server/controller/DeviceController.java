package com.syncml.server.controller;

import com.syncml.server.domain.Device;
import com.syncml.server.domain.EventLog;
import com.syncml.server.service.DeviceService;
import com.syncml.server.service.EventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // Vue 대시보드 연동용
public class DeviceController {

    private final DeviceService deviceService;
    private final EventLogService eventLogService;

    // 모든 단말 조회
    @GetMapping
    public List<Device> getAllDevices() {
        return deviceService.getAllDevices();
    }

    // 단말 상세 조회
    @GetMapping("/{vin}")
    public ResponseEntity<Device> getDevice(@PathVariable String vin) {
        return deviceService.getDevice(vin)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 단말 등록
    @PostMapping
    public Device registerDevice(@RequestBody DeviceRegisterRequest request) {
        return deviceService.registerDevice(request.vin(), request.model(), request.currentVersion());
    }

    // 단말 Heartbeat
    @PostMapping("/{vin}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable String vin) {
        deviceService.updateHeartbeat(vin);
        return ResponseEntity.ok().build();
    }

    // 단말 로그 조회
    @GetMapping("/{vin}/logs")
    public List<EventLog> getDeviceLogs(@PathVariable String vin) {
        return eventLogService.getLogsByVin(vin);
    }

    // 오프라인 단말 조회
    @GetMapping("/offline")
    public List<Device> getOfflineDevices() {
        return deviceService.getOfflineDevices();
    }

    // 요청 DTO
    public record DeviceRegisterRequest(String vin, String model, String currentVersion) {}
}

