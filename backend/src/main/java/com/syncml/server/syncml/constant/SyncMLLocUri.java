package com.syncml.server.syncml.constant;

/**
 * SyncML DM / FUMO LocURI constants.
 *
 * 프로토콜 경로 문자열은 서비스 로직에 직접 쓰지 않고 여기서 관리한다.
 */
public final class SyncMLLocUri {

    private SyncMLLocUri() {
    }

    public static final String DEV_INFO_PREFIX = "./DevInfo";
    public static final String FUMO_PREFIX = "./FUMO";

    public static final class Fumo {
        private Fumo() {
        }

        public static final String PKG_URL = FUMO_PREFIX + "/PkgURL";
        public static final String DOWNLOAD = FUMO_PREFIX + "/Download";
        public static final String INSTALL = FUMO_PREFIX + "/Install";
        public static final String PACKAGE_SIZE = FUMO_PREFIX + "/PackageSize";
        public static final String PACKAGE_HASH = FUMO_PREFIX + "/PackageHash";
        public static final String PACKAGE_SIGNATURE = FUMO_PREFIX + "/PackageSig";
        public static final String SIGNATURE_ALGORITHM = FUMO_PREFIX + "/SigAlg";
    }
}

