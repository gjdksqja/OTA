package com.syncml.server.syncml.service;

import com.syncml.server.domain.SyncSession;
import com.syncml.server.domain.SyncSessionStatus;
import com.syncml.server.repository.SyncSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SyncML 세션 관리 서비스
 *
 * SessionID를 통해 연속적인 SyncML 메시지 교환을 관리.
 * - 세션 생성/조회/종료
 * - MsgID/CmdID 관리
 * - 세션 타임아웃 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncMLSessionService {

    private final SyncSessionRepository sessionRepository;

    // 세션 타임아웃 (분)
    private static final int SESSION_TIMEOUT_MINUTES = 5;

    /**
     * 새 세션 생성
     * 클라이언트가 Alert 1201 (CLIENT_INITIATED_MGMT)로 세션 시작 요청 시 호출
     */
    @Transactional
    public SyncSession createSession(String vin) {
        // 기존 활성 세션이 있으면 종료
        terminateActiveSessions(vin);

        String sessionId = generateSessionId();
        SyncSession session = SyncSession.builder()
                .sessionId(sessionId)
                .vin(vin)
                .currentStep(1)
                .sessionStatus(SyncSessionStatus.INITIALIZED)
                .build();

        log.info("Created new session {} for VIN: {}", sessionId, vin);
        return sessionRepository.save(session);
    }

    /**
     * 기존 세션 조회 또는 새 세션 생성
     * 클라이언트가 SessionID를 보내면 해당 세션 반환, 없으면 새로 생성
     */
    @Transactional
    public SyncSession getOrCreateSession(String vin, String clientSessionId) {
        if (clientSessionId != null && !clientSessionId.isEmpty()) {
            Optional<SyncSession> existing = sessionRepository.findById(clientSessionId);
            if (existing.isPresent()) {
                SyncSession session = existing.get();
                // VIN 일치 확인
                if (session.getVin().equals(vin)) {
                    // 만료 확인
                    if (!session.isExpired(SESSION_TIMEOUT_MINUTES)) {
                        session.updateActivity();
                        return sessionRepository.save(session);
                    } else {
                        log.warn("Session {} expired, creating new one", clientSessionId);
                        session.setSessionStatus(SyncSessionStatus.EXPIRED);
                        sessionRepository.save(session);
                    }
                } else {
                    log.warn("Session {} VIN mismatch: expected {}, got {}",
                            clientSessionId, session.getVin(), vin);
                }
            }
        }
        return createSession(vin);
    }

    /**
     * 세션 조회
     */
    public Optional<SyncSession> getSession(String sessionId) {
        return sessionRepository.findById(sessionId);
    }

    /**
     * VIN으로 활성 세션 조회
     */
    public Optional<SyncSession> getActiveSession(String vin) {
        return sessionRepository.findByVinAndSessionStatusIn(
                vin,
                List.of(SyncSessionStatus.INITIALIZED,
                        SyncSessionStatus.AUTHENTICATED,
                        SyncSessionStatus.IN_PROGRESS)
        );
    }

    /**
     * 세션 인증 완료 처리
     */
    @Transactional
    public void markAuthenticated(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setAuthenticated(true);
            session.setSessionStatus(SyncSessionStatus.AUTHENTICATED);
            session.updateActivity();
            sessionRepository.save(session);
            log.info("Session {} authenticated", sessionId);
        });
    }

    /**
     * 세션 진행 상태로 변경
     */
    @Transactional
    public void markInProgress(String sessionId, Long jobId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setSessionStatus(SyncSessionStatus.IN_PROGRESS);
            session.setCurrentJobId(jobId);
            session.updateActivity();
            sessionRepository.save(session);
            log.info("Session {} now processing job {}", sessionId, jobId);
        });
    }

    /**
     * 세션 단계 진행
     */
    @Transactional
    public void advanceStep(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.advanceStep();
            sessionRepository.save(session);
            log.info("Session {} advanced to step {}", sessionId, session.getCurrentStep());
        });
    }

    /**
     * 다음 CmdID 발급
     */
    @Transactional
    public int getNextCmdId(String sessionId) {
        return sessionRepository.findById(sessionId)
                .map(session -> {
                    int nextId = session.nextCmdId();
                    sessionRepository.save(session);
                    return nextId;
                })
                .orElse(1);
    }

    /**
     * 마지막 MsgID 업데이트
     */
    @Transactional
    public void updateLastMsgId(String sessionId, int msgId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setLastMsgId(msgId);
            session.updateActivity();
            sessionRepository.save(session);
        });
    }

    /**
     * 세션 정상 종료
     */
    @Transactional
    public void completeSession(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.complete();
            sessionRepository.save(session);
            log.info("Session {} completed", sessionId);
        });
    }

    /**
     * 세션 실패 처리
     */
    @Transactional
    public void failSession(String sessionId, String reason) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.fail(reason);
            sessionRepository.save(session);
            log.warn("Session {} failed: {}", sessionId, reason);
        });
    }

    /**
     * VIN의 모든 활성 세션 종료
     */
    @Transactional
    public void terminateActiveSessions(String vin) {
        List<SyncSession> activeSessions = sessionRepository.findByVinAndSessionStatusIn(
                vin, List.of(SyncSessionStatus.INITIALIZED,
                        SyncSessionStatus.AUTHENTICATED,
                        SyncSessionStatus.IN_PROGRESS)
        ).stream().toList();

        // findByVinAndSessionStatusIn은 Optional을 반환하므로 수정 필요
        sessionRepository.findByVinOrderByStartedAtDesc(vin).stream()
                .filter(s -> s.getSessionStatus() == SyncSessionStatus.INITIALIZED
                        || s.getSessionStatus() == SyncSessionStatus.AUTHENTICATED
                        || s.getSessionStatus() == SyncSessionStatus.IN_PROGRESS)
                .forEach(session -> {
                    session.setSessionStatus(SyncSessionStatus.EXPIRED);
                    session.setEndedAt(LocalDateTime.now());
                    sessionRepository.save(session);
                    log.info("Terminated active session {} for VIN {}", session.getSessionId(), vin);
                });
    }

    /**
     * VIN의 세션 이력 조회
     */
    public List<SyncSession> getSessionHistory(String vin) {
        return sessionRepository.findByVinOrderByStartedAtDesc(vin);
    }

    /**
     * 만료 세션 정리 (5분마다 실행)
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        List<SyncSession> expired = sessionRepository.findExpiredSessions(
                List.of(SyncSessionStatus.INITIALIZED,
                        SyncSessionStatus.AUTHENTICATED,
                        SyncSessionStatus.IN_PROGRESS),
                threshold
        );

        expired.forEach(session -> {
            session.setSessionStatus(SyncSessionStatus.EXPIRED);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);
            log.info("Expired session {} due to timeout", session.getSessionId());
        });

        if (!expired.isEmpty()) {
            log.info("Cleaned up {} expired sessions", expired.size());
        }
    }

    /**
     * 세션 ID 생성
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}

