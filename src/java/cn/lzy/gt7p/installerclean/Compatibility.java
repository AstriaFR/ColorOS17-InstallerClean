package cn.lzy.gt7p.installerclean;

import android.content.pm.ApplicationInfo;
import java.lang.reflect.Field;

/** Read the version belonging to the package Vector is loading, without a Context. */
final class Compatibility {
    static long versionCode(ApplicationInfo info) throws ReflectiveOperationException {
        try {
            Field version = ApplicationInfo.class.getField("longVersionCode");
            return version.getLong(info);
        } catch (NoSuchFieldException legacyAndroid) {
            Field version = ApplicationInfo.class.getField("versionCode");
            return Integer.toUnsignedLong(version.getInt(info));
        }
    }
}
