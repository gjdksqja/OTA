package com.syncml.server.controller;

import com.syncml.server.domain.EventLog;
import com.syncml.server.domain.JobStatus;
import com.syncml.server.domain.UpdateJob;
import com.syncml.server.service.EventLogService;
import com.syncml.server.service.UpdateJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class JobController {

    private final UpdateJobService updateJobService;
    private final EventLogService eventLogService;

    // 모든 작업 조회
    @GetMapping
    public List<UpdateJob> getAllJobs() {
        return updateJobService.getAllJobs();
    }

    // 작업 생성 (업데이트 요청)
    @PostMapping
    public UpdateJob createJob(@RequestBody JobCreateRequest request) {
        return updateJobService.createJob(
                request.targetVin(),
                request.commandType(),
                request.payloadVersion(),
                request.pkgUrl()
        );
    }

    // 특정 VIN의 다음 작업 조회 (단말 시뮬레이터 Polling용)
    @GetMapping("/next/{vin}")
    public ResponseEntity<UpdateJob> getNextJob(@PathVariable String vin) {
        return updateJobService.getNextJobForVin(vin)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // 작업 상태 변경
    @PatchMapping("/{jobId}/status")
    public ResponseEntity<Void> updateJobStatus(
            @PathVariable Long jobId,
            @RequestBody JobStatusUpdateRequest request) {
        updateJobService.updateJobStatus(jobId, request.status());
        return ResponseEntity.ok().build();
    }

    // 작업 실패 처리
    @PostMapping("/{jobId}/fail")
    public ResponseEntity<Void> failJob(
            @PathVariable Long jobId,
            @RequestBody JobFailRequest request) {
        updateJobService.failJob(jobId, request.errorMessage());
        return ResponseEntity.ok().build();
    }

    // 특정 VIN의 작업 목록
    @GetMapping("/vin/{vin}")
    public List<UpdateJob> getJobsByVin(@PathVariable String vin) {
        return updateJobService.getJobsByVin(vin);
    }

    // 작업 로그 조회
    @GetMapping("/{jobId}/logs")
    public List<EventLog> getJobLogs(@PathVariable Long jobId) {
        return eventLogService.getLogsByJobId(jobId);
    }

    // 요청 DTOs
    public record JobCreateRequest(
            String targetVin,
            String commandType,
            String payloadVersion,
            String pkgUrl
    ) {}

    public record JobStatusUpdateRequest(JobStatus status) {}

    public record JobFailRequest(String errorMessage) {}
}

