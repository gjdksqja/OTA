package com.syncml.server.repository;

import com.syncml.server.domain.SyncSession;
import com.syncml.server.domain.SyncSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncSessionRepository extends JpaRepository<SyncSession, String> {

    // VIN으로 활성 세션 조회
    Optional<SyncSession> findByVinAndSessionStatusIn(String vin, List<SyncSessionStatus> statuses);

    // VIN의 가장 최근 세션 조회
    Optional<SyncSession> findTopByVinOrderByStartedAtDesc(String vin);

    // 활성 세션 목록
    List<SyncSession> findBySessionStatusIn(List<SyncSessionStatus> statuses);

    // 만료된 세션 조회 (특정 시간 이전에 마지막 활동)
    @Query("SELECT s FROM SyncSession s WHERE s.sessionStatus IN :statuses AND s.lastActivityAt < :threshold")
    List<SyncSession> findExpiredSessions(
            @Param("statuses") List<SyncSessionStatus> statuses,
            @Param("threshold") LocalDateTime threshold
    );

    // VIN의 세션 이력 조회
    List<SyncSession> findByVinOrderByStartedAtDesc(String vin);
}

