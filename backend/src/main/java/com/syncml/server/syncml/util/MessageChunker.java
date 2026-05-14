package com.syncml.server.syncml.util;

import com.syncml.server.syncml.dto.Command;
import com.syncml.server.syncml.dto.SyncMLMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SyncML 송신 측 Large Object 청킹.
 *
 * 단순 데모 수준 구현:
 *  - 메시지를 직렬화하기 직전 호출
 *  - device.maxMsgSize 가 주어지면, 그 크기를 넘기는 Item.data 를
 *    여러 Item 으로 쪼개고 마지막을 제외한 모든 Item 에 MoreData=true 부착
 *  - 진짜 SyncML 표준은 "메시지 사이를 가로질러" 청킹하지만, 본 프로젝트에서는
 *    한 응답 메시지 안에서의 Item 분할을 시연한다 (포트폴리오용).
 *
 * 관련 표준: OMA DM 1.2, SyncML Representation Protocol §5.3.5 (MoreData)
 */
@Component
@Slf4j
public class MessageChunker {

    /** 안전 마진: XML envelope/ tag 오버헤드 감안 */
    private static final int OVERHEAD_MARGIN_BYTES = 512;

    /**
     * @param message    응답 메시지 (mutate 됨)
     * @param maxMsgSize 단말 ./DevInfo/Ext/MaxMsgSize, null 이면 청킹 안 함
     */
    public void chunkIfNeeded(SyncMLMessage message, Long maxMsgSize) {
        if (message == null || message.getSyncBody() == null) return;
        if (maxMsgSize == null || maxMsgSize <= 0) return;

        long chunkSize = Math.max(64, maxMsgSize - OVERHEAD_MARGIN_BYTES);
        List<Command> commands = message.getSyncBody().getCommands();
        if (commands == null) return;

        for (Command cmd : commands) {
            if (cmd.getItems() == null) continue;
            List<Command.Item> rebuilt = new ArrayList<>();
            for (Command.Item item : cmd.getItems()) {
                rebuilt.addAll(splitItem(item, chunkSize));
            }
            cmd.setItems(rebuilt);
        }
    }

    private List<Command.Item> splitItem(Command.Item item, long chunkSize) {
        List<Command.Item> out = new ArrayList<>();
        String data = item.getData();
        if (data == null || data.length() <= chunkSize) {
            out.add(item);
            return out;
        }

        int total = data.length();
        int offset = 0;
        int idx = 0;
        while (offset < total) {
            int end = (int) Math.min(offset + chunkSize, total);
            String chunk = data.substring(offset, end);
            boolean isLast = end >= total;

            Command.Item piece = Command.Item.builder()
                    .sourceUri(item.getSourceUri())
                    .targetUri(item.getTargetUri())
                    .format(item.getFormat())
                    .type(item.getType())
                    .data(chunk)
                    .moreData(!isLast)
                    .build();
            out.add(piece);
            offset = end;
            idx++;
        }
        log.info("Item split: target={} totalBytes={} chunks={} chunkSize~={}",
                item.getTargetUri(), total, idx, chunkSize);
        return out;
    }
}

