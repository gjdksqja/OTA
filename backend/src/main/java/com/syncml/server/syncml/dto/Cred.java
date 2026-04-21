package com.syncml.server.syncml.dto;

import lombok.*;

/**
 * SyncML Cred (인증) 요소
 *
 * <Cred>
 *   <Meta>
 *     <Format>b64</Format>
 *     <Type>syncml:auth-basic</Type>
 *   </Meta>
 *   <Data>dXNlcjpwYXNz</Data>  <!-- Base64 encoded -->
 * </Cred>
 *
 * 지원 인증 타입:
 * - syncml:auth-basic : Base64(username:password)
 * - syncml:auth-md5   : Base64(MD5(username:password:nonce))
 * - syncml:auth-hmac  : HMAC-based authentication
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cred {
    private String format;    // 데이터 포맷 (b64 = Base64)
    private String type;      // 인증 타입
    private String data;      // 인코딩된 인증 데이터
}

