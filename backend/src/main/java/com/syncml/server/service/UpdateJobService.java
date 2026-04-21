package com.syncml.server.service;

import com.syncml.server.domain.*;
import com.syncml.server.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UpdateJobService {

    private final UpdateJobRepository updateJobRepository;
    private final DeviceRepository deviceRepository;
    private final EventLogService eventLogService;

    // 업데이트 작업 생성 (큐에 등록)
    public UpdateJob createJob(String targetVin, String commandType, String payloadVersion, String pkgUrl) {
        // 단말 존재 확인
        deviceRepository.findById(targetVin)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + targetVin));

        UpdateJob job = UpdateJob.builder()
                .targetVin(targetVin)
                .commandType(commandType)
                .payloadVersion(payloadVersion)
                .pkgUrl(pkgUrl)
                .status(JobStatus.QUEUED)
                .build();

        UpdateJob saved = updateJobRepository.save(job);

        // 단말 목표 버전 설정
        deviceRepository.findById(targetVin).ifPresent(device -> {
            device.setTargetVersion(payloadVersion);
            deviceRepository.save(device);
        });

        eventLogService.log(targetVin, saved.getJobId(), null, "JOB_CREATED",
                "작업 생성: " + commandType + " -> " + payloadVersion);

        log.info("Job created: {} for VIN {}", saved.getJobId(), targetVin);
        return saved;
    }

    // 특정 VIN의 다음 작업 가져오기 (Polling용)
    @Transactional(readOnly = true)
    public Optional<UpdateJob> getNextJobForVin(String vin) {
        return updateJobRepository.findNextJobForVin(vin);
    }

    // 작업 상태 변경
    public void updateJobStatus(Long jobId, JobStatus status) {
        updateJobRepository.findById(jobId).ifPresent(job -> {
            JobStatus oldStatus = job.getStatus();
            job.setStatus(status);
            updateJobRepository.save(job);

            // 단말 상태도 연동
            if (status == JobStatus.ASSIGNED || status == JobStatus.DOWNLOADING || status == JobStatus.INSTALLING) {
                updateDeviceStatus(job.getTargetVin(), DeviceStatus.UPDATING);
            } else if (status == JobStatus.SUCCESS) {
                updateDeviceStatus(job.getTargetVin(), DeviceStatus.IDLE);
            } else if (status == JobStatus.FAIL) {
                updateDeviceStatus(job.getTargetVin(), DeviceStatus.FAILED);
            }

            eventLogService.log(job.getTargetVin(), jobId, null, "STATUS_CHANGED",
                    oldStatus + " -> " + status);
            log.info("Job {} status: {} -> {}", jobId, oldStatus, status);
        });
    }

    // 작업 실패 처리
    public void failJob(Long jobId, String errorMessage) {
        updateJobRepository.findById(jobId).ifPresent(job -> {
            job.incrementRetry();

            if (job.canRetry()) {
                // 재시도 가능: QUEUED로 복귀
                job.setStatus(JobStatus.QUEUED);
                job.setErrorMessage(errorMessage + " (retry " + job.getRetryCount() + "/3)");
                eventLogService.log(job.getTargetVin(), jobId, null, "JOB_RETRY",
                        "재시도 예정 (" + job.getRetryCount() + "/3): " + errorMessage);
            } else {
                // 재시도 불가: FAIL 확정
                job.setStatus(JobStatus.FAIL);
                job.setErrorMessage(errorMessage);
                updateDeviceStatus(job.getTargetVin(), DeviceStatus.FAILED);
                eventLogService.log(job.getTargetVin(), jobId, null, "JOB_FAILED",
                        "최종 실패: " + errorMessage);
            }

            updateJobRepository.save(job);
        });
    }

    // 작업 목록 조회
    @Transactional(readOnly = true)
    public List<UpdateJob> getJobsByVin(String vin) {
        return updateJobRepository.findByTargetVinOrderByCreatedAtAsc(vin);
    }

    // 전체 작업 조회
    @Transactional(readOnly = true)
    public List<UpdateJob> getAllJobs() {
        return updateJobRepository.findAll();
    }

    // VIN에 진행 중인 작업이 있는지 확인
    @Transactional(readOnly = true)
    public boolean hasActiveJob(String vin) {
        return updateJobRepository.hasActiveJob(vin);
    }

    private void updateDeviceStatus(String vin, DeviceStatus status) {
        deviceRepository.findById(vin).ifPresent(device -> {
            device.setDeviceStatus(status);
            deviceRepository.save(device);
        });
    }
}

