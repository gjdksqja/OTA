package com.syncml.server.domain;

/**
 * SyncML 세션 상태
 */
public enum SyncSessionStatus {
    INITIALIZED,      // 세션 생성됨 (아직 인증 안됨)
    AUTHENTICATED,    // 인증 완료
    IN_PROGRESS,      // FUMO 흐름 진행 중
    COMPLETED,        // 정상 종료
    FAILED,           // 실패
    EXPIRED           // 타임아웃으로 만료
}

