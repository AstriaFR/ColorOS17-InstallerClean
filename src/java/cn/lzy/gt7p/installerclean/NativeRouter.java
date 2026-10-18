package cn.lzy.gt7p.installerclean;

import android.content.Context;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Use the vendor's decoupled entry, staging service and appdetail UI. */
final class NativeRouter {
    private static final String TAG = "GT7PInstallerClean";
    static int entryResult(boolean installing, long frontendVersion, int original) {
        if (!installing || Profile.select(frontendVersion) == null) return original;
        // Feature-switch fallbacks; caller, session and package validation remain original.
        return original == 1 || original == 11 ? 0 : original;
    }
    static Method method(ClassLoader loader, String owner, String name, Class<?> result, Class<?>... params) throws Exception {
        Method method = Class.forName(owner, false, loader).getDeclaredMethod(name, params);
        if (method.getReturnType() != result) throw new NoSuchMethodException(owner + "#" + name + " return type");
        method.setAccessible(true);
        return method;
    }
    static void install(XposedModule module, ClassLoader loader, long versionCode) throws Throwable {
        if (versionCode != Profile.NATIVE_VERSION) return;
        Method entry = method(loader, "m1.c", "k", int.class, Context.class, boolean.class, String.class);
        Method start = method(loader, "com.android.packageinstaller.InstallStart", "onCreate", void.class, android.os.Bundle.class);
        module.hook(entry).setId("installer:appdetail:entry").intercept(chain -> {
            int original = (Integer) chain.proceed();
            Context context = (Context) chain.getArg(0);
            long frontend;
            try { frontend = Compatibility.versionCode(context.getPackageManager().getApplicationInfo(Profile.PACKAGE, 0)); }
            catch (Exception unavailable) { return original; }
            int result = entryResult((Boolean) chain.getArg(1), frontend, original);
            Log.i(TAG, "ROUTE appdetail-native frontend=" + frontend + " original=" + original + " result=" + result);
            return result;
        });
        module.deoptimize(entry);
        module.deoptimize(start);
        module.log(Log.INFO, TAG, "ACTIVE appdetail-native-entry nativeVersionCode=" + versionCode + " hooks=1");
        Log.i(TAG, "ACTIVE appdetail-native-entry nativeVersionCode=" + versionCode + " hooks=1");
    }
}
