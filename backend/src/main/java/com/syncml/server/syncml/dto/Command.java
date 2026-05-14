package com.syncml.server.syncml.dto;

import lombok.*;
import java.util.List;

/**
 * SyncML 명령 (Get, Exec, Replace, Add, Delete 등)
 *
 * <Get>
 *   <CmdID>2</CmdID>
 *   <Item>
 *     <Target><LocURI>./DevInfo/SwV</LocURI></Target>
 *   </Item>
 * </Get>
 *
 * <Exec>
 *   <CmdID>3</CmdID>
 *   <Item>
 *     <Target><LocURI>./FUMO/Download</LocURI></Target>
 *   </Item>
 * </Exec>
 *
 * <Replace>
 *   <CmdID>4</CmdID>
 *   <Item>
 *     <Target><LocURI>./FUMO/PkgURL</LocURI></Target>
 *     <Data>http://server/packages/update.zip</Data>
 *   </Item>
 * </Replace>
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Command {
    private CommandType type;     // GET, EXEC, REPLACE, ADD, DELETE
    private Integer cmdId;
    private List<Item> items;

    public enum CommandType {
        GET, EXEC, REPLACE, ADD, DELETE, COPY, ATOMIC, SEQUENCE
    }

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String sourceUri;
        private String targetUri;
        private String format;
        private String type;
        private String data;
        /** SyncML <MoreData/> - 이 Item이 다음 메시지/Item으로 이어진다는 표시 */
        @Builder.Default
        private boolean moreData = false;
    }
}

