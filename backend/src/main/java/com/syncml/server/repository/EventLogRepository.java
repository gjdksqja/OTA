package com.syncml.server.repository;

import com.syncml.server.domain.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    List<EventLog> findByVinOrderByCreatedAtDesc(String vin);

    List<EventLog> findByJobIdOrderByCreatedAtAsc(Long jobId);

    List<EventLog> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}

