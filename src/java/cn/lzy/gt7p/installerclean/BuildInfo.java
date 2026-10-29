package cn.lzy.gt7p.installerclean;

/** Identity of the code actually loaded; independent of PackageManager's latest APK metadata. */
final class BuildInfo {
    static final long VERSION_CODE = 123L;
    static final String REVISION = "v1.1.1";
    static String identity() { return "module=" + VERSION_CODE + " revision=" + REVISION; }
}
