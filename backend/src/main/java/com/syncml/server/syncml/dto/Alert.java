package com.syncml.server.syncml.dto;

import lombok.*;

/**
 * SyncML Alert 요소
 *
 * <Alert>
 *   <CmdID>1</CmdID>
 *   <Data>1201</Data>  <!-- Alert 코드 -->
 *   <Item>
 *     <Source><LocURI>./DevInfo</LocURI></Source>
 *   </Item>
 * </Alert>
 *
 * 주요 Alert 코드:
 * - 1200: SERVER_INITIATED_MGMT (서버 시작 관리 세션)
 * - 1201: CLIENT_INITIATED_MGMT (클라이언트 시작 관리 세션)
 * - 1222: NEXT_MESSAGE (다음 메시지 요청)
 * - 1223: SESSION_ABORT (세션 중단)
 * - 1224: CLIENT_EVENT (클라이언트 이벤트)
 * - 1226: GENERIC_ALERT_WITH_DATA
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {
    private Integer cmdId;
    private Integer data;        // Alert 코드
    private String correlator;   // 상관 ID (선택)
    private Item item;           // 관련 아이템

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String sourceUri;
        private String targetUri;
        private String data;
    }
}

