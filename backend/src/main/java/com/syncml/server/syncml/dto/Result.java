package com.syncml.server.syncml.dto;

import lombok.*;
import java.util.List;

/**
 * SyncML Results 요소
 * Get 명령에 대한 응답
 *
 * <Results>
 *   <CmdID>3</CmdID>
 *   <MsgRef>1</MsgRef>
 *   <CmdRef>2</CmdRef>
 *   <Item>
 *     <Source><LocURI>./DevInfo/SwV</LocURI></Source>
 *     <Data>1.0.0</Data>
 *   </Item>
 * </Results>
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Result {
    private Integer cmdId;
    private Integer msgRef;
    private Integer cmdRef;
    private List<Item> items;

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String sourceUri;
        private String targetUri;
        private String format;    // 데이터 포맷
        private String type;      // MIME 타입
        private String data;      // 실제 데이터
        /** SyncML <MoreData/> - 추가 청크가 뒤에 더 온다는 표시 */
        @Builder.Default
        private boolean moreData = false;
    }
}

