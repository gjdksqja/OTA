package com.syncml.server.syncml.dto;

import lombok.*;

/**
 * SyncML Status 요소
 *
 * <Status>
 *   <CmdID>1</CmdID>
 *   <MsgRef>1</MsgRef>
 *   <CmdRef>0</CmdRef>     <!-- 0 = SyncHdr에 대한 응답 -->
 *   <Cmd>SyncHdr</Cmd>
 *   <Data>200</Data>       <!-- 상태 코드 -->
 * </Status>
 *
 * 주요 상태 코드:
 * - 200: OK
 * - 212: Authentication accepted
 * - 401: Unauthorized (인증 필요)
 * - 407: Authentication required (Cred 필요)
 * - 500: Command failed
 * - 516: Atomic failed
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Status {
    private Integer cmdId;
    private Integer msgRef;    // 참조하는 메시지 ID
    private Integer cmdRef;    // 참조하는 명령 ID (0 = SyncHdr)
    private String cmd;        // 참조하는 명령 이름
    private Integer data;      // 상태 코드
    private String targetRef;  // 참조하는 타겟 URI
    private String sourceRef;  // 참조하는 소스 URI
    private Chal chal;         // 인증 챌린지 (401/407 응답 시)

    /**
     * SyncML 인증 챌린지
     * 401/407 응답 시 클라이언트에게 인증 방법을 알려줌
     */
    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Chal {
        private String type;     // 요구 인증 타입 (예: syncml:auth-md5)
        private String format;   // 포맷 (b64)
        private String nonce;    // MD5/HMAC용 nonce (Base64 인코딩)
    }
}

