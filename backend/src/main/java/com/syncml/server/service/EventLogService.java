package com.syncml.server.service;

import com.syncml.server.domain.EventLog;
import com.syncml.server.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EventLogService {

    private final EventLogRepository eventLogRepository;

    // 이벤트 로그 기록
    public EventLog log(String vin, Long jobId, String sessionId, String eventType, String message) {
        EventLog eventLog = EventLog.builder()
                .vin(vin)
                .jobId(jobId)
                .sessionId(sessionId)
                .eventType(eventType)
                .message(message)
                .build();
        return eventLogRepository.save(eventLog);
    }

    // SyncML XML 포함 로그
    public EventLog logWithXml(String vin, Long jobId, String sessionId, String eventType, String message, String rawXml) {
        EventLog eventLog = EventLog.builder()
                .vin(vin)
                .jobId(jobId)
                .sessionId(sessionId)
                .eventType(eventType)
                .message(message)
                .rawXml(rawXml)
                .build();
        return eventLogRepository.save(eventLog);
    }

    // VIN별 로그 조회
    @Transactional(readOnly = true)
    public List<EventLog> getLogsByVin(String vin) {
        return eventLogRepository.findByVinOrderByCreatedAtDesc(vin);
    }

    // 작업별 로그 조회
    @Transactional(readOnly = true)
    public List<EventLog> getLogsByJobId(Long jobId) {
        return eventLogRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    }

    // 세션별 로그 조회
    @Transactional(readOnly = true)
    public List<EventLog> getLogsBySessionId(String sessionId) {
        return eventLogRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}

