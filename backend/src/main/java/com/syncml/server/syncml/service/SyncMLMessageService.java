package com.syncml.server.syncml.service;

import com.syncml.server.domain.*;
import com.syncml.server.repository.DeviceRepository;
import com.syncml.server.repository.UpdateJobRepository;
import com.syncml.server.service.DeviceService;
import com.syncml.server.service.EventLogService;
import com.syncml.server.syncml.constant.DevInfoNode;
import com.syncml.server.syncml.constant.SyncMLLocUri;
import com.syncml.server.syncml.dto.*;
import com.syncml.server.syncml.util.SyncMLXmlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * SyncML 메시지 처리 핵심 서비스
 *
 * FUMO 흐름:
 * 1. Client → Server: Alert (1201) + DevInfo (세션 시작)
 * 2. Server → Client: Get VersionInfo
 * 3. Client → Server: Results VersionInfo
 * 4. Server → Client: Replace (PkgURL)
 * 5. Server → Client: Exec (Update)
 * 6. Client → Server: Status (결과)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncMLMessageService {

    private final SyncMLSessionService sessionService;
    private final SyncMLAuthService authService;
    private final SyncMLXmlUtil xmlUtil;
    private final MessageChunker messageChunker;
    private final ChunkReassemblyService chunkReassemblyService;
    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final UpdateJobRepository jobRepository;
    private final EventLogService eventLogService;

    // SyncML 상태 코드
    private static final int STATUS_OK = 200;
    private static final int STATUS_AUTH_ACCEPTED = 212;
    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_COMMAND_FAILED = 500;

    // Alert 코드
    private static final int ALERT_CLIENT_INITIATED = 1201;

    /**
     * SyncML 메시지 처리 메인 엔트리
     */
    @Transactional
    public String processMessage(String xmlRequest) {
        try {
            SyncMLMessage request = xmlUtil.parse(xmlRequest);
            SyncMLMessage response = handleMessage(request);

            // 응답 직렬화 직전: 단말 maxMsgSize 기반 Large Object 청킹
            String vin = extractVin(request.getSyncHdr());
            Long maxMsgSize = deviceRepository.findById(vin)
                    .map(Device::getMaxMsgSize)
                    .orElse(null);
            messageChunker.chunkIfNeeded(response, maxMsgSize);

            return xmlUtil.toXml(response);
        } catch (Exception e) {
            log.error("Failed to process SyncML message", e);
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * 메시지 처리 로직
     */
    private SyncMLMessage handleMessage(SyncMLMessage request) {
        SyncHdr reqHdr = request.getSyncHdr();
        String vin = extractVin(reqHdr);

        log.info("Processing SyncML message from VIN: {}, SessionID: {}, MsgID: {}",
                vin, reqHdr.getSessionId(), reqHdr.getMsgId());

        // 1. 세션 관리
        SyncSession session = sessionService.getOrCreateSession(vin, reqHdr.getSessionId());
        sessionService.updateLastMsgId(session.getSessionId(), reqHdr.getMsgId());

        // 2. 인증 처리
        if (!session.getAuthenticated()) {
            SyncMLAuthService.AuthResult authResult = authService.authenticate(vin, reqHdr.getCred());

            if (authResult.requiresAuth()) {
                // 인증 필요 - 401 응답
                String nonce = authResult.nonce();
                authService.updateNonce(vin, nonce);
                return createAuthRequiredResponse(reqHdr, session, nonce);
            } else if (!authResult.success()) {
                // 인증 실패
                log.warn("Auth failed for VIN: {}", vin);
                return createAuthFailedResponse(reqHdr, session);
            }
            // 인증 성공
            sessionService.markAuthenticated(session.getSessionId());
        }

        // 3. Device heartbeat 갱신
        deviceService.updateHeartbeat(vin);

        // 4. 메시지 바디 처리
        List<Status> responseStatuses = new ArrayList<>();
        List<Command> responseCommands = new ArrayList<>();

        // SyncHdr에 대한 Status (항상)
        responseStatuses.add(createHdrStatus(reqHdr, session));

        // Alert 처리
        if (request.getSyncBody() != null && request.getSyncBody().getAlerts() != null) {
            for (Alert alert : request.getSyncBody().getAlerts()) {
                responseStatuses.add(handleAlert(alert, reqHdr, session, vin));
            }
        }

        // Status 처리 (클라이언트의 명령 결과)
        if (request.getSyncBody() != null && request.getSyncBody().getStatuses() != null) {
            for (Status status : request.getSyncBody().getStatuses()) {
                handleClientStatus(status, session, vin);
            }
        }

        // Results 처리 (Get에 대한 응답)
        if (request.getSyncBody() != null && request.getSyncBody().getResults() != null) {
            for (Result result : request.getSyncBody().getResults()) {
                handleResults(result, session, vin);
            }
        }

        // DevInfo 사전 교환 - 클라이언트가 첫 메시지에 ./DevInfo/* Replace 로 동봉한 경우 (Pattern A: client-push)
        if (request.getSyncBody() != null && request.getSyncBody().getCommands() != null) {
            for (Command cmd : request.getSyncBody().getCommands()) {
                if (cmd.getType() == Command.CommandType.REPLACE) {
                    handleDevInfoReplace(cmd, vin, reqHdr, responseStatuses, session);
                }
            }
        }

        // 5. 다음 명령 결정 (FUMO 흐름)
        responseCommands.addAll(determineNextCommands(session, vin));

        // 6. 응답 생성
        return createResponse(reqHdr, session, responseStatuses, responseCommands);
    }

    /**
     * Alert 처리
     */
    private Status handleAlert(Alert alert, SyncHdr reqHdr, SyncSession session, String vin) {
        int cmdId = sessionService.getNextCmdId(session.getSessionId());

        if (alert.getData() != null && alert.getData() == ALERT_CLIENT_INITIATED) {
            // 클라이언트가 세션 시작
            log.info("Client initiated session for VIN: {}", vin);
            eventLogService.log(vin, null, "SESSION_START",
                    "Client initiated SyncML session");

            // 대기 중인 작업 확인
            Optional<UpdateJob> pendingJob = jobRepository
                    .findFirstByTargetVinAndStatusOrderByCreatedAtAsc(vin, JobStatus.QUEUED);

            if (pendingJob.isPresent()) {
                UpdateJob job = pendingJob.get();
                job.setStatus(JobStatus.ASSIGNED);
                jobRepository.save(job);
                sessionService.markInProgress(session.getSessionId(), job.getJobId());
                eventLogService.log(vin, job.getJobId(), "JOB_ASSIGNED",
                        "Job " + job.getJobId() + " assigned to session");
            }
        }

        return Status.builder()
                .cmdId(cmdId)
                .msgRef(reqHdr.getMsgId())
                .cmdRef(alert.getCmdId())
                .cmd("Alert")
                .data(STATUS_OK)
                .build();
    }

    /**
     * 클라이언트 Status 처리 (명령 결과)
     *
     * Step 5: 다운로드 완료 Status (DOWNLOADING → step 5)
     * Step 7: 설치 완료 Status (INSTALLING → step 7)
     */
    private void handleClientStatus(Status status, SyncSession session, String vin) {
        String cmd = status.getCmd();
        int statusCode = status.getData() != null ? status.getData() : 0;
        int currentStep = session.getCurrentStep();

        log.info("Client status for {} at step {}: Cmd={}, Status={}", vin, currentStep, cmd, statusCode);

        // Exec 명령 결과 처리 (다운로드/설치)
        if ("Exec".equals(cmd) && session.getCurrentJobId() != null) {
            UpdateJob job = jobRepository.findById(session.getCurrentJobId()).orElse(null);
            if (job == null) return;

            if (statusCode == 200) {
                // 성공
                if (job.getStatus() == JobStatus.DOWNLOADING) {
                    // Step 5: 다운로드 완료 → Step 6 준비
                    eventLogService.log(vin, job.getJobId(), session.getSessionId(),
                            "DOWNLOAD_COMPLETE", "Download completed successfully");
                    sessionService.advanceStep(session.getSessionId());
                } else if (job.getStatus() == JobStatus.INSTALLING) {
                    // Step 7: 설치 완료 → Step 8 준비
                    eventLogService.log(vin, job.getJobId(), session.getSessionId(),
                            "INSTALL_COMPLETE", "Install completed successfully");
                    sessionService.advanceStep(session.getSessionId());
                }
            } else {
                // 실패
                JobStatus failedAt = job.getStatus();
                job.setStatus(JobStatus.FAIL);
                job.setErrorMessage("Command failed with status: " + statusCode);
                jobRepository.save(job);
                sessionService.failSession(session.getSessionId(), "Command failed");
                chunkReassemblyService.clearSession(session.getSessionId());

                String failType = failedAt == JobStatus.DOWNLOADING ? "DOWNLOAD_FAILED" : "INSTALL_FAILED";
                eventLogService.log(vin, job.getJobId(), session.getSessionId(), failType,
                        "Failed with status: " + statusCode);
            }
        }
    }

    /**
     * Results 처리 (Get 응답)
     * - MoreData 청킹 reassembly 적용
     * - ./DevInfo/* 응답이면 Device 갱신 (Pattern B: server-pull DevInfo)
     */
    private void handleResults(Result result, SyncSession session, String vin) {
        if (result.getItems() == null) return;

        boolean stepShouldAdvance = false;
        for (Result.Item item : result.getItems()) {
            String uri = item.getSourceUri();
            // 청크 reassembly: MoreData 가 false 인 마지막 청크에서만 완전한 데이터를 얻음
            Optional<String> assembled =
                    chunkReassemblyService.accept(session.getSessionId(), result.getCmdId(), item);
            if (assembled.isEmpty()) {
                // 아직 청크가 더 와야 함
                continue;
            }
            String data = assembled.get();
            log.info("Received result for {}: {} = {} ({} bytes)",
                    vin, uri, abbreviate(data), data == null ? 0 : data.length());

            if (uri == null) continue;

            if (DevInfoNode.isDevInfoUri(uri)) {
                applyDevInfoNode(vin, uri, data);
            } else if (uri.contains("SwV")) {
                deviceService.getDevice(vin).ifPresent(device -> {
                    device.setCurrentVersion(data);
                    deviceRepository.save(device);
                });
            }
            stepShouldAdvance = true;
        }
        if (stepShouldAdvance) {
            sessionService.advanceStep(session.getSessionId());
        }
    }

    /**
     * Pattern A: Client-Push DevInfo - Pkg #1 에 Replace ./DevInfo/* 형태로 들어온 트리.
     * 각 ./DevInfo/* Item 을 Device 엔티티에 매핑하고, Status 200 을 응답에 추가한다.
     */
    private void handleDevInfoReplace(Command cmd, String vin, SyncHdr reqHdr,
                                       List<Status> responseStatuses, SyncSession session) {
        if (cmd.getItems() == null) return;
        boolean any = false;
        for (Command.Item item : cmd.getItems()) {
            String uri = item.getTargetUri();
            if (!DevInfoNode.isDevInfoUri(uri)) continue;
            applyDevInfoNode(vin, uri, item.getData());
            any = true;
        }
        if (any) {
            responseStatuses.add(Status.builder()
                    .cmdId(sessionService.getNextCmdId(session.getSessionId()))
                    .msgRef(reqHdr.getMsgId())
                    .cmdRef(cmd.getCmdId())
                    .cmd("Replace")
                    .data(STATUS_OK)
                    .build());
            log.info("DevInfo (client-push) applied for VIN {}", vin);
        }
    }

    /**
     * ./DevInfo/* LocURI -> Device 컬럼 매핑
     * 표준 노드: Man, Mod, DmV, Lang, DevId, SwV, Ext/MaxMsgSize, Ext/MaxObjSize, Ext/SupportLargeObj
     */
    private void applyDevInfoNode(String locUri, String value, Device device) {
        if (value == null) return;
        DevInfoNode.fromLocUri(locUri).ifPresentOrElse(node -> {
            switch (node) {
                case MANUFACTURER -> device.setManufacturer(value);
                case MODEL -> device.setModel(value);
                case DM_CLIENT_VERSION -> device.setDmClientVersion(value);
                case LANGUAGE -> device.setLang(value);
                case DEV_ID -> device.setDevId(value);
                case SOFTWARE_VERSION -> device.setCurrentVersion(value);
                case MAX_MSG_SIZE -> device.setMaxMsgSize(parseLongSafe(value));
                case MAX_OBJ_SIZE -> {
                    Long v = parseLongSafe(value);
                    device.setMaxObjSize(v);
                    if (v != null && v > 0) device.setSupportLargeObj(true);
                }
                case SUPPORT_LARGE_OBJECT -> device.setSupportLargeObj(Boolean.parseBoolean(value));
            }
        }, () -> log.debug("Unhandled DevInfo node: {} = {}", locUri, value));
    }

    private void applyDevInfoNode(String vin, String locUri, String value) {
        deviceRepository.findById(vin).ifPresent(d -> {
            applyDevInfoNode(locUri, value, d);
            deviceRepository.save(d);
        });
    }

    private Long parseLongSafe(String v) {
        try { return Long.parseLong(v.trim()); } catch (Exception e) { return null; }
    }

    private String abbreviate(String s) {
        if (s == null) return null;
        return s.length() <= 80 ? s : s.substring(0, 80) + "...";
    }

    /**
     * 다음 명령 결정 (FUMO 9단계)
     *
     * Step 1: Client → Alert 1201 + Cred (세션 시작)
     * Step 2: Server → Get SwV (버전 확인)
     * Step 3: Client → Results SwV (버전 응답)
     * Step 4: Server → Replace PkgURL (다운로드 URL 전달)
     * Step 5: Client → Status 200 (다운로드 완료 보고)
     * Step 6: Server → Exec Install (설치 명령)
     * Step 7: Client → Status 200 (설치 완료 보고)
     * Step 8: Server → Final (세션 종료)
     * Step 9: Client 대기 (다음 Poll까지)
     */
    private List<Command> determineNextCommands(SyncSession session, String vin) {
        List<Command> commands = new ArrayList<>();
        int step = session.getCurrentStep();

        log.debug("Determining next commands for VIN {} at step {}", vin, step);

        // Job 없이 세션만 있는 경우 (버전 확인만)
        if (session.getCurrentJobId() == null) {
            if (step == 1) {
                // Step 2: 버전 확인 요청
                commands.add(createGetCommand(session, DevInfoNode.SOFTWARE_VERSION.locUri()));
                sessionService.advanceStep(session.getSessionId());
            }
            // 작업 없으면 Final로 종료 (handleMessage에서 처리)
            return commands;
        }

        // 진행 중인 Job이 있는 경우
        UpdateJob job = jobRepository.findById(session.getCurrentJobId()).orElse(null);
        if (job == null) {
            return commands;
        }

        switch (step) {
            case 1 -> {
                // Step 2: Get VersionInfo (인증 후 첫 응답)
                // Pattern B (server-pull): 단말이 ./DevInfo 를 아직 알려주지 않았다면 같이 요청
                Device device = deviceRepository.findById(vin).orElse(null);
                if (device == null || device.getMaxMsgSize() == null) {
                    commands.add(createGetCommand(session, DevInfoNode.MAX_MSG_SIZE.locUri()));
                    commands.add(createGetCommand(session, DevInfoNode.MAX_OBJ_SIZE.locUri()));
                    commands.add(createGetCommand(session, DevInfoNode.MANUFACTURER.locUri()));
                    commands.add(createGetCommand(session, DevInfoNode.DM_CLIENT_VERSION.locUri()));
                }
                commands.add(createGetCommand(session, DevInfoNode.SOFTWARE_VERSION.locUri()));
                sessionService.advanceStep(session.getSessionId());
            }
            case 2 -> {
                // Step 3 대기: Results 받기 전 (handleResults에서 step 증가)
            }
            case 3 -> {
                // Step 4: Replace PkgURL (다운로드 URL 전달)
                if (job.getPkgUrl() != null) {
                    commands.add(createReplaceCommand(session, SyncMLLocUri.Fumo.PKG_URL, job.getPkgUrl()));
                    // 다운로드 시작 표시를 위해 Exec Download도 같이
                    commands.add(createExecCommand(session, SyncMLLocUri.Fumo.DOWNLOAD));
                    job.setStatus(JobStatus.DOWNLOADING);
                    jobRepository.save(job);
                    sessionService.advanceStep(session.getSessionId());
                    eventLogService.log(vin, job.getJobId(), session.getSessionId(),
                            "DOWNLOAD_START", "Download started: " + job.getPkgUrl());
                }
            }
            case 4 -> {
                // Step 5 대기: 다운로드 완료 Status 대기 (handleClientStatus에서 처리)
            }
            case 5 -> {
                // Step 6: Exec Install (다운로드 성공 후 설치 명령)
                commands.add(createExecCommand(session, SyncMLLocUri.Fumo.INSTALL));
                job.setStatus(JobStatus.INSTALLING);
                jobRepository.save(job);
                sessionService.advanceStep(session.getSessionId());
                eventLogService.log(vin, job.getJobId(), session.getSessionId(),
                        "INSTALL_START", "Install started");
            }
            case 6 -> {
                // Step 7 대기: 설치 완료 Status 대기 (handleClientStatus에서 처리)
            }
            case 7 -> {
                // Step 8: Final (세션 종료는 createResponse에서 처리)
                job.setStatus(JobStatus.SUCCESS);
                jobRepository.save(job);
                sessionService.completeSession(session.getSessionId());
                chunkReassemblyService.clearSession(session.getSessionId());
                eventLogService.log(vin, job.getJobId(), session.getSessionId(),
                        "JOB_COMPLETE", "Job completed successfully");
            }
        }

        return commands;
    }

    // ========== 명령 생성 헬퍼 ==========

    private Command createGetCommand(SyncSession session, String targetUri) {
        return Command.builder()
                .type(Command.CommandType.GET)
                .cmdId(sessionService.getNextCmdId(session.getSessionId()))
                .items(List.of(Command.Item.builder()
                        .targetUri(targetUri)
                        .build()))
                .build();
    }

    private Command createReplaceCommand(SyncSession session, String targetUri, String data) {
        return Command.builder()
                .type(Command.CommandType.REPLACE)
                .cmdId(sessionService.getNextCmdId(session.getSessionId()))
                .items(List.of(Command.Item.builder()
                        .targetUri(targetUri)
                        .data(data)
                        .build()))
                .build();
    }

    private Command createExecCommand(SyncSession session, String targetUri) {
        return Command.builder()
                .type(Command.CommandType.EXEC)
                .cmdId(sessionService.getNextCmdId(session.getSessionId()))
                .items(List.of(Command.Item.builder()
                        .targetUri(targetUri)
                        .build()))
                .build();
    }

    // ========== 응답 생성 ==========

    private Status createHdrStatus(SyncHdr reqHdr, SyncSession session) {
        return Status.builder()
                .cmdId(sessionService.getNextCmdId(session.getSessionId()))
                .msgRef(reqHdr.getMsgId())
                .cmdRef(0)  // SyncHdr는 CmdRef=0
                .cmd("SyncHdr")
                .data(session.getAuthenticated() ? STATUS_AUTH_ACCEPTED : STATUS_OK)
                .targetRef(reqHdr.getSourceUri())
                .sourceRef(reqHdr.getTargetUri())
                .build();
    }

    private SyncMLMessage createResponse(SyncHdr reqHdr, SyncSession session,
                                          List<Status> statuses, List<Command> commands) {
        SyncHdr respHdr = SyncHdr.builder()
                .verDTD("1.2")
                .verProto("DM/1.2")
                .sessionId(session.getSessionId())
                .msgId(reqHdr.getMsgId())
                .targetUri(reqHdr.getSourceUri())
                .sourceUri(reqHdr.getTargetUri())
                .build();

        return SyncMLMessage.builder()
                .syncHdr(respHdr)
                .syncBody(SyncMLMessage.SyncBody.builder()
                        .statuses(statuses)
                        .commands(commands)
                        .finalFlag(true)
                        .build())
                .build();
    }

    private SyncMLMessage createAuthRequiredResponse(SyncHdr reqHdr, SyncSession session, String nonce) {
        Status authStatus = Status.builder()
                .cmdId(1)
                .msgRef(reqHdr.getMsgId())
                .cmdRef(0)
                .cmd("SyncHdr")
                .data(STATUS_UNAUTHORIZED)
                .chal(authService.createChallenge("syncml:auth-basic", nonce))
                .build();

        return createResponse(reqHdr, session, List.of(authStatus), List.of());
    }

    private SyncMLMessage createAuthFailedResponse(SyncHdr reqHdr, SyncSession session) {
        Status authStatus = Status.builder()
                .cmdId(1)
                .msgRef(reqHdr.getMsgId())
                .cmdRef(0)
                .cmd("SyncHdr")
                .data(STATUS_UNAUTHORIZED)
                .build();

        return createResponse(reqHdr, session, List.of(authStatus), List.of());
    }

    private String createErrorResponse(String errorMessage) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <SyncML xmlns="SYNCML:SYNCML1.2">
                  <SyncHdr>
                    <VerDTD>1.2</VerDTD>
                    <VerProto>DM/1.2</VerProto>
                    <SessionID>error</SessionID>
                    <MsgID>1</MsgID>
                  </SyncHdr>
                  <SyncBody>
                    <Status>
                      <CmdID>1</CmdID>
                      <MsgRef>1</MsgRef>
                      <CmdRef>0</CmdRef>
                      <Cmd>SyncHdr</Cmd>
                      <Data>500</Data>
                    </Status>
                    <Final/>
                  </SyncBody>
                </SyncML>
                """.formatted();
    }

    private String extractVin(SyncHdr hdr) {
        // LocName이 있으면 사용, 없으면 SourceUri에서 추출
        if (hdr.getSourceName() != null && !hdr.getSourceName().isEmpty()) {
            return hdr.getSourceName();
        }
        // IMEI:xxx 형태에서 추출
        String source = hdr.getSourceUri();
        if (source != null && source.contains(":")) {
            return source.split(":")[1];
        }
        return source;
    }
}

