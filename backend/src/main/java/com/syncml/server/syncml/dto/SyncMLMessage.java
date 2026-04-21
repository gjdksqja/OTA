package com.syncml.server.syncml.dto;

import lombok.*;
import java.util.List;

/**
 * SyncML 메시지 (요청/응답 공용)
 *
 * <SyncML>
 *   <SyncHdr>...</SyncHdr>
 *   <SyncBody>
 *     <Alert>...</Alert>
 *     <Status>...</Status>
 *     <Results>...</Results>
 *     <Exec>...</Exec>
 *     <Replace>...</Replace>
 *     <Get>...</Get>
 *   </SyncBody>
 * </SyncML>
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncMLMessage {
    private SyncHdr syncHdr;
    private SyncBody syncBody;

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncBody {
        private List<Alert> alerts;
        private List<Status> statuses;
        private List<Result> results;
        private List<Command> commands;  // Get, Exec, Replace 등
        private boolean finalFlag;       // <Final/> 플래그
    }
}

