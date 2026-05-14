package com.syncml.server.syncml.constant;

import java.util.Arrays;
import java.util.Optional;

/**
 * 이 프로젝트에서 처리하는 표준 ./DevInfo 노드 목록.
 *
 * 표준으로 의미를 아는 노드는 Enum으로 고정하고,
 * 벤더 확장/미지원 노드는 서비스에서 로그만 남기고 무시한다.
 */
public enum DevInfoNode {
    DEV_ID("./DevInfo/DevId"),
    MANUFACTURER("./DevInfo/Man"),
    MODEL("./DevInfo/Mod"),
    DM_CLIENT_VERSION("./DevInfo/DmV"),
    LANGUAGE("./DevInfo/Lang"),
    SOFTWARE_VERSION("./DevInfo/SwV"),
    MAX_MSG_SIZE("./DevInfo/Ext/MaxMsgSize"),
    MAX_OBJ_SIZE("./DevInfo/Ext/MaxObjSize"),
    SUPPORT_LARGE_OBJECT("./DevInfo/Ext/SupportLargeObj");

    private final String locUri;

    DevInfoNode(String locUri) {
        this.locUri = locUri;
    }

    public String locUri() {
        return locUri;
    }

    public static boolean isDevInfoUri(String locUri) {
        return locUri != null && locUri.startsWith(SyncMLLocUri.DEV_INFO_PREFIX);
    }

    public static Optional<DevInfoNode> fromLocUri(String locUri) {
        return Arrays.stream(values())
                .filter(node -> node.locUri.equals(locUri))
                .findFirst();
    }
}

