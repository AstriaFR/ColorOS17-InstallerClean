package cn.lzy.gt7p.installerclean;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import dalvik.system.PathClassLoader;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Use the vendor's decoupled entry, staging service and appdetail UI. */
final class NativeRouter {
    private static final String TAG = "GT7PInstallerClean";
    private static String cachedPath;
    private static boolean cachedCompatible;
    record Plan(Method entry, Method start) { }
    static Plan prepare(ClassLoader loader) throws ReflectiveOperationException {
        StructuralProbe.Prepared matched = StructuralProbe.prepare(loader, null, Profile.NATIVE_GROUP);
        return new Plan(matched.targets().get(0), (Method) matched.callers().get(0));
    }
    private static synchronized boolean frontendCompatible(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(Profile.PACKAGE,
                    android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES);
            List<String> paths = new ArrayList<>();
            paths.add(info.sourceDir);
            if (info.splitSourceDirs != null) paths.addAll(Arrays.asList(info.splitSourceDirs));
            if (info.sharedLibraryFiles != null) paths.addAll(Arrays.asList(info.sharedLibraryFiles));
            String path = String.join(File.pathSeparator, paths);
            String key = path;
            for (String item : paths) {
                File apk = new File(item);
                key += ":" + apk.lastModified() + ":" + apk.length();
            }
            if (key.equals(cachedPath)) return cachedCompatible;
            // Isolate the frontend APK from the backend's app classes; inspect without initialization.
            ClassLoader loader = new PathClassLoader(path, ClassLoader.getSystemClassLoader());
            StructuralProbe.Decision decision = StructuralProbe.select(loader, Feature.FLOW, null);
            cachedCompatible = decision.selected() != null;
            cachedPath = key;
            Log.w(TAG, BuildInfo.identity() + " ROUTE_PROBE frontendCompatible=" + cachedCompatible
                    + " ambiguous=" + decision.ambiguous());
            return cachedCompatible;
        } catch (Exception | LinkageError unavailable) { return false; }
    }
    static XposedModule.HookHandle install(XposedModule module, Plan plan) throws Throwable {
        XposedModule.HookHandle handle = module.hook(plan.entry()).setId("installer:appdetail:entry").intercept(chain -> {
            int original = (Integer) chain.proceed();
            Context context = (Context) chain.getArg(0);
            boolean installing = (Boolean) chain.getArg(1);
            if (!installing || original != 1 && original != 11) return original;
            int result = RoutingPolicy.result(true, frontendCompatible(context), original);
            Log.i(TAG, "ROUTE appdetail-native original=" + original + " result=" + result);
            return result;
        });
        try {
            module.deoptimize(plan.entry());
            module.deoptimize(plan.start());
        } catch (Throwable failure) { handle.unhook(); throw failure; }
        return handle;
    }
}
