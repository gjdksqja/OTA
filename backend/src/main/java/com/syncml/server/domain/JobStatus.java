package com.syncml.server.domain;

public enum JobStatus {
    QUEUED,         // 대기열에 등록됨
    ASSIGNED,       // 단말에 할당됨
    DOWNLOADING,    // 다운로드 중
    INSTALLING,     // 설치 중
    SUCCESS,        // 성공
    FAIL            // 실패
}

