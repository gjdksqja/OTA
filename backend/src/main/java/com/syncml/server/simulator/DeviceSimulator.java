package com.syncml.server.simulator;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 단말 시뮬레이터
 *
 * 실제 차량/단말을 흉내내어 SyncML 서버와 통신.
 * - Poll 주기: 5초 (작업 확인)
 * - Heartbeat: 10초 (생존 알림)
 * - 다운로드/설치 시간 시뮬레이션
 */
@Slf4j
@Getter
public class DeviceSimulator implements Runnable {

    private final String vin;
    private final String username;
    private final String password;
    private final String serverUrl;
    private final HttpClient httpClient;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger sessionMsgId = new AtomicInteger(0);
    private String currentSessionId;
    private String currentVersion = "1.0.0";

    // 설정
    private static final int POLL_INTERVAL_MS = 5000;      // 5초
    private static final int HEARTBEAT_INTERVAL_MS = 10000; // 10초
    private static final int DOWNLOAD_MIN_MS = 2000;        // 2초
    private static final int DOWNLOAD_MAX_MS = 5000;        // 5초
    private static final int INSTALL_MIN_MS = 3000;         // 3초
    private static final int INSTALL_MAX_MS = 8000;         // 8초

    private final Random random = new Random();
    private long lastHeartbeat = 0;

    public DeviceSimulator(String vin, String username, String password, String serverUrl) {
        this.vin = vin;
        this.username = username;
        this.password = password;
        this.serverUrl = serverUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void run() {
        log.info("[{}] 시뮬레이터 시작 (버전: {})", vin, currentVersion);

        while (running.get()) {
            try {
                // Heartbeat 체크
                if (System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_INTERVAL_MS) {
                    sendHeartbeat();
                    lastHeartbeat = System.currentTimeMillis();
                }

                // SyncML 세션 시작 (Poll)
                startSyncMLSession();

                // Poll 주기 대기
                Thread.sleep(POLL_INTERVAL_MS);

            } catch (InterruptedException e) {
                log.info("[{}] 시뮬레이터 중단됨", vin);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[{}] 오류 발생: {}", vin, e.getMessage());
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("[{}] 시뮬레이터 종료", vin);
    }

    /**
     * SyncML 세션 시작 (Step 1: Alert 1201)
     */
    private void startSyncMLSession() throws Exception {
        currentSessionId = generateSessionId();
        sessionMsgId.set(1);

        log.info("[{}] SyncML 세션 시작: {}", vin, currentSessionId);

        // Step 1: Alert 1201 + Cred 전송
        String request = buildAlertMessage();
        String response = sendSyncML(request);

        if (response == null) {
            log.warn("[{}] 서버 응답 없음", vin);
            return;
        }

        // 응답 파싱 및 처리
        processSyncMLResponse(response);
    }

    /**
     * SyncML 응답 처리 (서버 명령 실행)
     */
    private void processSyncMLResponse(String response) throws Exception {
        // 간단한 파싱 (실제로는 XML 파서 사용)

        // 인증 실패 확인
        if (response.contains("<Data>401</Data>") || response.contains("<Data>407</Data>")) {
            log.warn("[{}] 인증 실패", vin);
            return;
        }

        // Final 확인 (할 일 없음)
        if (response.contains("<Final/>") && !response.contains("<Get>") && !response.contains("<Replace>")) {
            log.debug("[{}] 할 일 없음, 세션 종료", vin);
            return;
        }

        // Get 명령 확인 (버전 요청)
        if (response.contains("<Get>") && response.contains("SwV")) {
            log.info("[{}] 버전 요청 받음, 현재 버전: {}", vin, currentVersion);
            sessionMsgId.incrementAndGet();
            String resultsMsg = buildResultsMessage();
            String nextResponse = sendSyncML(resultsMsg);
            if (nextResponse != null) {
                processSyncMLResponse(nextResponse);
            }
            return;
        }

        // Replace 명령 확인 (다운로드 URL)
        if (response.contains("<Replace>") && response.contains("PkgURL")) {
            String pkgUrl = extractData(response, "PkgURL");
            log.info("[{}] 다운로드 명령 받음: {}", vin, pkgUrl);

            // 다운로드 시뮬레이션
            simulateDownload();

            // 다운로드 완료 Status 전송
            sessionMsgId.incrementAndGet();
            String statusMsg = buildStatusMessage("Exec", 200, "Download");
            String nextResponse = sendSyncML(statusMsg);
            if (nextResponse != null) {
                processSyncMLResponse(nextResponse);
            }
            return;
        }

        // Exec Install 명령 확인
        if (response.contains("<Exec>") && response.contains("Install")) {
            log.info("[{}] 설치 명령 받음", vin);

            // 설치 시뮬레이션
            boolean success = simulateInstall();

            // 설치 결과 Status 전송
            sessionMsgId.incrementAndGet();
            int statusCode = success ? 200 : 500;
            String statusMsg = buildStatusMessage("Exec", statusCode, "Install");
            String nextResponse = sendSyncML(statusMsg);

            if (success) {
                currentVersion = "2.0.0"; // 버전 업데이트
                log.info("[{}] 업데이트 완료, 새 버전: {}", vin, currentVersion);
            }

            if (nextResponse != null) {
                processSyncMLResponse(nextResponse);
            }
            return;
        }

        // Final (세션 종료)
        if (response.contains("<Final/>")) {
            log.info("[{}] 세션 종료", vin);
        }
    }

    /**
     * 다운로드 시뮬레이션 (2~5초)
     */
    private void simulateDownload() throws InterruptedException {
        int downloadTime = DOWNLOAD_MIN_MS + random.nextInt(DOWNLOAD_MAX_MS - DOWNLOAD_MIN_MS);
        log.info("[{}] 다운로드 중... ({}ms)", vin, downloadTime);
        Thread.sleep(downloadTime);
        log.info("[{}] 다운로드 완료", vin);
    }

    /**
     * 설치 시뮬레이션 (3~8초, 10% 실패 확률)
     */
    private boolean simulateInstall() throws InterruptedException {
        int installTime = INSTALL_MIN_MS + random.nextInt(INSTALL_MAX_MS - INSTALL_MIN_MS);
        log.info("[{}] 설치 중... ({}ms)", vin, installTime);
        Thread.sleep(installTime);

        // 10% 확률로 실패
        if (random.nextInt(10) == 0) {
            log.error("[{}] 설치 실패 (시뮬레이션)", vin);
            return false;
        }

        log.info("[{}] 설치 완료", vin);
        return true;
    }

    /**
     * Heartbeat 전송
     */
    private void sendHeartbeat() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/devices/" + vin + "/heartbeat"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("[{}] Heartbeat 전송", vin);
        } catch (Exception e) {
            log.warn("[{}] Heartbeat 실패: {}", vin, e.getMessage());
        }
    }

    /**
     * SyncML 메시지 전송
     */
    private String sendSyncML(String xmlMessage) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/syncml"))
                .header("Content-Type", "application/vnd.syncml.dm+xml")
                .POST(HttpRequest.BodyPublishers.ofString(xmlMessage))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            log.warn("[{}] HTTP 오류: {}", vin, response.statusCode());
            return null;
        }
    }

    // ========== XML 메시지 빌더 ==========

    private String buildAlertMessage() {
        String cred = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <SyncML xmlns="SYNCML:SYNCML1.2">
              <SyncHdr>
                <VerDTD>1.2</VerDTD>
                <VerProto>DM/1.2</VerProto>
                <SessionID>%s</SessionID>
                <MsgID>%d</MsgID>
                <Target><LocURI>%s/syncml</LocURI></Target>
                <Source>
                  <LocURI>IMEI:%s</LocURI>
                  <LocName>%s</LocName>
                </Source>
                <Cred>
                  <Meta>
                    <Format>b64</Format>
                    <Type>syncml:auth-basic</Type>
                  </Meta>
                  <Data>%s</Data>
                </Cred>
              </SyncHdr>
              <SyncBody>
                <Alert>
                  <CmdID>1</CmdID>
                  <Data>1201</Data>
                </Alert>
                <Final/>
              </SyncBody>
            </SyncML>
            """, currentSessionId, sessionMsgId.get(), serverUrl, vin, vin, cred);
    }

    private String buildResultsMessage() {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <SyncML xmlns="SYNCML:SYNCML1.2">
              <SyncHdr>
                <VerDTD>1.2</VerDTD>
                <VerProto>DM/1.2</VerProto>
                <SessionID>%s</SessionID>
                <MsgID>%d</MsgID>
                <Target><LocURI>%s/syncml</LocURI></Target>
                <Source>
                  <LocURI>IMEI:%s</LocURI>
                  <LocName>%s</LocName>
                </Source>
              </SyncHdr>
              <SyncBody>
                <Status>
                  <CmdID>1</CmdID>
                  <MsgRef>%d</MsgRef>
                  <CmdRef>0</CmdRef>
                  <Cmd>SyncHdr</Cmd>
                  <Data>200</Data>
                </Status>
                <Results>
                  <CmdID>2</CmdID>
                  <MsgRef>%d</MsgRef>
                  <CmdRef>1</CmdRef>
                  <Item>
                    <Source><LocURI>./DevInfo/SwV</LocURI></Source>
                    <Data>%s</Data>
                  </Item>
                </Results>
                <Final/>
              </SyncBody>
            </SyncML>
            """, currentSessionId, sessionMsgId.get(), serverUrl, vin, vin,
                sessionMsgId.get() - 1, sessionMsgId.get() - 1, currentVersion);
    }

    private String buildStatusMessage(String cmd, int statusCode, String context) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <SyncML xmlns="SYNCML:SYNCML1.2">
              <SyncHdr>
                <VerDTD>1.2</VerDTD>
                <VerProto>DM/1.2</VerProto>
                <SessionID>%s</SessionID>
                <MsgID>%d</MsgID>
                <Target><LocURI>%s/syncml</LocURI></Target>
                <Source>
                  <LocURI>IMEI:%s</LocURI>
                  <LocName>%s</LocName>
                </Source>
              </SyncHdr>
              <SyncBody>
                <Status>
                  <CmdID>1</CmdID>
                  <MsgRef>%d</MsgRef>
                  <CmdRef>0</CmdRef>
                  <Cmd>SyncHdr</Cmd>
                  <Data>200</Data>
                </Status>
                <Status>
                  <CmdID>2</CmdID>
                  <MsgRef>%d</MsgRef>
                  <CmdRef>1</CmdRef>
                  <Cmd>%s</Cmd>
                  <Data>%d</Data>
                </Status>
                <Final/>
              </SyncBody>
            </SyncML>
            """, currentSessionId, sessionMsgId.get(), serverUrl, vin, vin,
                sessionMsgId.get() - 1, sessionMsgId.get() - 1, cmd, statusCode);
    }

    // ========== 유틸리티 ==========

    private String generateSessionId() {
        return vin + "-" + System.currentTimeMillis();
    }

    private String extractData(String xml, String key) {
        // 간단한 추출 (실제로는 XML 파서 사용)
        int start = xml.indexOf(key);
        if (start == -1) return null;
        int dataStart = xml.indexOf("<Data>", start);
        int dataEnd = xml.indexOf("</Data>", dataStart);
        if (dataStart == -1 || dataEnd == -1) return null;
        return xml.substring(dataStart + 6, dataEnd);
    }

    public void stop() {
        running.set(false);
    }
}

