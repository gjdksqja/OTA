-- ============================================
-- SyncML OTA 시스템 초기 스키마
-- ============================================

-- 단말 테이블
CREATE TABLE device (
    vin VARCHAR(50) PRIMARY KEY,
    model VARCHAR(100),
    current_version VARCHAR(50),
    target_version VARCHAR(50),
    last_seen_at TIMESTAMP DEFAULT NOW(),
    device_status VARCHAR(20) DEFAULT 'IDLE',  -- IDLE, UPDATING, FAILED
    created_at TIMESTAMP DEFAULT NOW()
);

-- 업데이트 작업 테이블
CREATE TABLE update_job (
    job_id SERIAL PRIMARY KEY,
    target_vin VARCHAR(50) REFERENCES device(vin),
    command_type VARCHAR(50) NOT NULL,  -- FUMO_UPDATE, SCOMO_INSTALL 등
    payload_version VARCHAR(50),
    pkg_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'QUEUED',  -- QUEUED, ASSIGNED, DOWNLOADING, INSTALLING, SUCCESS, FAIL
    retry_count INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- SyncML 세션 테이블
CREATE TABLE sync_session (
    session_id VARCHAR(100) PRIMARY KEY,
    vin VARCHAR(50) REFERENCES device(vin),
    current_step INT DEFAULT 1,
    last_cmd_id INT DEFAULT 0,
    session_status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, COMPLETED, TIMEOUT, ERROR
    started_at TIMESTAMP DEFAULT NOW(),
    ended_at TIMESTAMP
);

-- 이벤트 로그 테이블
CREATE TABLE event_log (
    id SERIAL PRIMARY KEY,
    vin VARCHAR(50),
    job_id INT,
    session_id VARCHAR(100),
    event_type VARCHAR(50) NOT NULL,  -- SESSION_START, ALERT_RECEIVED, EXEC_SENT, DOWNLOAD_START, INSTALL_COMPLETE, ERROR
    message TEXT,
    raw_xml TEXT,  -- SyncML 원본 메시지 (디버깅용)
    created_at TIMESTAMP DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_device_status ON device(device_status);
CREATE INDEX idx_job_status ON update_job(status);
CREATE INDEX idx_job_vin ON update_job(target_vin);
CREATE INDEX idx_session_vin ON sync_session(vin);
CREATE INDEX idx_log_vin ON event_log(vin);
CREATE INDEX idx_log_job ON event_log(job_id);
CREATE INDEX idx_log_created ON event_log(created_at);

-- 초기 테스트 데이터
INSERT INTO device (vin, model, current_version, device_status) VALUES
    ('VIN-0001', 'TEST-MODEL-A', '1.0.0', 'IDLE'),
    ('VIN-0002', 'TEST-MODEL-B', '1.0.0', 'IDLE'),
    ('VIN-0003', 'TEST-MODEL-C', '1.1.0', 'IDLE');

