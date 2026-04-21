package com.syncml.server.repository;

import com.syncml.server.domain.JobStatus;
import com.syncml.server.domain.UpdateJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UpdateJobRepository extends JpaRepository<UpdateJob, Long> {

    // 특정 VIN의 작업 조회
    List<UpdateJob> findByTargetVinOrderByCreatedAtAsc(String targetVin);

    // 특정 VIN의 대기 중인 작업 조회 (가장 오래된 것 먼저)
    @Query("SELECT j FROM UpdateJob j WHERE j.targetVin = :vin AND j.status = :status ORDER BY j.createdAt ASC")
    List<UpdateJob> findByTargetVinAndStatus(@Param("vin") String vin, @Param("status") JobStatus status);

    // VIN에 대해 현재 진행 중인 작업이 있는지 확인 (동시 1개 제한)
    @Query("SELECT COUNT(j) > 0 FROM UpdateJob j WHERE j.targetVin = :vin AND j.status IN ('ASSIGNED', 'DOWNLOADING', 'INSTALLING')")
    boolean hasActiveJob(@Param("vin") String vin);

    // 다음 처리할 작업 가져오기 (QUEUED 상태, VIN에 진행 중인 작업 없음)
    @Query("""
        SELECT j FROM UpdateJob j 
        WHERE j.targetVin = :vin 
        AND j.status = 'QUEUED' 
        AND NOT EXISTS (
            SELECT 1 FROM UpdateJob j2 
            WHERE j2.targetVin = :vin 
            AND j2.status IN ('ASSIGNED', 'DOWNLOADING', 'INSTALLING')
        )
        ORDER BY j.createdAt ASC
        LIMIT 1
        """)
    Optional<UpdateJob> findNextJobForVin(@Param("vin") String vin);

    // 상태별 작업 수 조회
    long countByStatus(JobStatus status);

    // VIN + 상태로 첫 번째 작업 조회 (가장 오래된 것)
    Optional<UpdateJob> findFirstByTargetVinAndStatusOrderByCreatedAtAsc(String targetVin, JobStatus status);
}

