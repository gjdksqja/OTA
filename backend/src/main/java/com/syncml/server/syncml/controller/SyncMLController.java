package com.syncml.server.syncml.controller;

import com.syncml.server.syncml.service.SyncMLMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SyncML 엔드포인트 컨트롤러
 *
 * 단말이 SyncML XML 메시지를 POST로 전송하면 처리 후 XML 응답 반환.
 *
 * Content-Type: application/vnd.syncml.dm+xml
 * 또는: application/xml, text/xml
 */
@RestController
@RequestMapping("/syncml")
@RequiredArgsConstructor
@Slf4j
public class SyncMLController {

    private final SyncMLMessageService messageService;

    // SyncML DM 표준 미디어 타입
    private static final String SYNCML_DM_MEDIA_TYPE = "application/vnd.syncml.dm+xml";

    /**
     * SyncML 메시지 처리 엔드포인트
     *
     * 단말이 이 엔드포인트로 SyncML XML을 POST.
     * 서버는 처리 후 SyncML XML 응답 반환.
     */
    @PostMapping(
            consumes = {SYNCML_DM_MEDIA_TYPE, MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = {SYNCML_DM_MEDIA_TYPE, MediaType.APPLICATION_XML_VALUE}
    )
    public ResponseEntity<String> handleSyncML(@RequestBody String xmlRequest) {
        log.debug("Received SyncML request:\n{}", xmlRequest);

        String xmlResponse = messageService.processMessage(xmlRequest);

        log.debug("Sending SyncML response:\n{}", xmlResponse);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(SYNCML_DM_MEDIA_TYPE))
                .body(xmlResponse);
    }

    /**
     * 헬스 체크 (단말이 서버 접근 가능 여부 확인용)
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SyncML Server is running");
    }
}

