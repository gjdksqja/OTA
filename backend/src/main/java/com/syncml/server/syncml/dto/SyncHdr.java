package com.syncml.server.syncml.dto;

import lombok.*;

/**
 * SyncML SyncHdr 요소 매핑
 *
 * <SyncHdr>
 *   <VerDTD>1.2</VerDTD>
 *   <VerProto>DM/1.2</VerProto>
 *   <SessionID>1</SessionID>
 *   <MsgID>1</MsgID>
 *   <Target><LocURI>http://server/syncml</LocURI></Target>
 *   <Source>
 *     <LocURI>IMEI:123456789</LocURI>
 *     <LocName>VIN-0001</LocName>
 *   </Source>
 *   <Cred>
 *     <Meta><Type>syncml:auth-basic</Type></Meta>
 *     <Data>dXNlcjpwYXNz</Data>  <!-- Base64(user:pass) -->
 *   </Cred>
 * </SyncHdr>
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncHdr {
    private String verDTD;        // SyncML DTD 버전 (예: 1.2)
    private String verProto;      // 프로토콜 버전 (예: DM/1.2)
    private String sessionId;     // 세션 ID
    private Integer msgId;        // 메시지 ID
    private String targetUri;     // 서버 URI
    private String sourceUri;     // 단말 식별자 (IMEI 등)
    private String sourceName;    // 단말 이름 (VIN)
    private Cred cred;            // 인증 정보 (선택)
}

