package com.syncml.server.syncml.controller;

import com.syncml.server.domain.SyncSession;
import com.syncml.server.syncml.service.SyncMLSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SyncML 세션 조회 API
 * Vue 대시보드에서 세션 상태를 조회하기 위한 엔드포인트
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SyncSessionController {

    private final SyncMLSessionService sessionService;

    /**
     * 특정 세션 조회
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<SyncSession> getSession(@PathVariable String sessionId) {
        return sessionService.getSession(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * VIN의 활성 세션 조회
     */
    @GetMapping("/active/{vin}")
    public ResponseEntity<SyncSession> getActiveSession(@PathVariable String vin) {
        return sessionService.getActiveSession(vin)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * VIN의 세션 이력 조회
     */
    @GetMapping("/history/{vin}")
    public List<SyncSession> getSessionHistory(@PathVariable String vin) {
        return sessionService.getSessionHistory(vin);
    }
}

