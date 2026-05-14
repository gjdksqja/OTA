package com.syncml.server.syncml.service;

import com.syncml.server.syncml.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SyncML Large Object (MoreData) 수신 측 reassembly 버퍼.
 *
 * SyncML 1.2 표준: 같은 CmdID 로 들어오는 Item들을 순서대로 누적하다가
 * MoreData 가 없는 마지막 청크를 받으면 전체 데이터를 반환한다.
 *
 * 메모리 보관(In-Memory) 으로 충분 - 세션 단위로 살아있고 종료 시 정리.
 */
@Service
@Slf4j
public class ChunkReassemblyService {

    /** sessionId -> (cmdId -> 누적 버퍼) */
    private final Map<String, Map<Integer, StringBuilder>> buffers = new ConcurrentHashMap<>();

    /**
     * Result.Item 청크 처리.
     *
     * @return 마지막 청크면 합쳐진 전체 데이터(Optional present), 아직 더 와야 하면 Optional.empty()
     */
    public Optional<String> accept(String sessionId, Integer cmdId, Result.Item item) {
        if (cmdId == null) {
            // CmdID 없으면 청킹 추적 불가 - 그대로 반환
            return Optional.ofNullable(item.getData());
        }

        Map<Integer, StringBuilder> sessionBuf =
                buffers.computeIfAbsent(sessionId, k -> new HashMap<>());

        StringBuilder sb = sessionBuf.computeIfAbsent(cmdId, k -> new StringBuilder());
        if (item.getData() != null) {
            sb.append(item.getData());
        }

        if (item.isMoreData()) {
            log.debug("Chunk buffered: session={} cmdId={} accumulated={} bytes",
                    sessionId, cmdId, sb.length());
            return Optional.empty();
        }

        // 마지막 청크
        String complete = sb.toString();
        sessionBuf.remove(cmdId);
        log.info("Chunk reassembled: session={} cmdId={} totalLength={}",
                sessionId, cmdId, complete.length());
        return Optional.of(complete);
    }

    /** 세션 종료 시 호출하여 메모리 정리 */
    public void clearSession(String sessionId) {
        buffers.remove(sessionId);
    }
}

