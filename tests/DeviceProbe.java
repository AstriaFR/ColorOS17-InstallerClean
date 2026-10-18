package cn.lzy.gt7p.installerclean;

import dalvik.system.PathClassLoader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated app_process: uses real pulled APK classes but performs no install/session/settings operations. */
public final class DeviceProbe {
    public static void main(String[] args) throws Throwable {
        android.os.Looper.prepareMainLooper();
        PathClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        long versionCode = Long.parseLong(args[1]);
        Class<?> profile = Class.forName("cn.lzy.gt7p.installerclean.Profile", true, loader);
        Method select = profile.getDeclaredMethod("select", long.class);
        select.setAccessible(true);
        Object rule = select.invoke(null, versionCode);
        check(rule != null);
        String[][] specs = (String[][]) field(rule, "hooks");
        Class<?> adapterClass = Class.forName("cn.lzy.gt7p.installerclean.InstallerHook$Adapter", true, loader);
        Constructor<?> adapterConstructor = adapterClass.getDeclaredConstructor(ClassLoader.class, rule.getClass());
        adapterConstructor.setAccessible(true);
        Object adapter = adapterConstructor.newInstance(loader, rule);
        Method resolve = adapterClass.getDeclaredMethod("method", String.class, String.class, String.class, String[].class);
        resolve.setAccessible(true);
        for (String[] spec : specs) {
            resolve.invoke(adapter, spec[1], spec[2], spec[3], Arrays.copyOfRange(spec, 4, spec.length));
        }
        for (String[] caller : (String[][]) field(rule, "deopt")) {
            boolean found = caller[1].equals("<init>") && Class.forName(caller[0], false, loader).getDeclaredConstructors().length > 0;
            for (Method method : Class.forName(caller[0], false, loader).getDeclaredMethods()) {
                if (method.getName().equals(caller[1])) found = true;
            }
            check(found);
        }
        Class<?> chainType = Class.forName("io.github.libxposed.api.XposedInterface$Chain", false, loader);
        Method dispatch = adapterClass.getDeclaredMethod("dispatch", String.class, chainType);
        dispatch.setAccessible(true);
        InvocationHandler rejectProceed = (proxy, method, values) -> {
            throw new AssertionError("Replacement unexpectedly used original chain: " + method.getName());
        };
        Object chain = Proxy.newProxyInstance(loader, new Class<?>[]{chainType}, rejectProceed);
        int assertions = 1;
        for (long unknown : new long[]{-1, 0, 51761, 51763, 52301, 52303, 99999}) {
            check(select.invoke(null, unknown) == null); assertions++;
        }
        Object other = select.invoke(null, versionCode == 51762 ? 52302L : 51762L);
        check(other != null && other != rule); assertions++;
        android.content.pm.ApplicationInfo info = new android.content.pm.ApplicationInfo();
        Field versionField = android.content.pm.ApplicationInfo.class.getField("longVersionCode");
        versionField.setLong(info, versionCode);
        info.sourceDir = "/different/location/not-hashed.apk";
        info.splitSourceDirs = new String[]{"/different/split.apk"};
        Class<?> compatibility = Class.forName("cn.lzy.gt7p.installerclean.Compatibility", true, loader);
        Method readVersion = compatibility.getDeclaredMethod("versionCode", android.content.pm.ApplicationInfo.class);
        readVersion.setAccessible(true);
        check(((Long) readVersion.invoke(null, info)) == versionCode); assertions++;
        Object guide = dispatch.invoke(adapter, "GUIDE", chain);
        Object config = guide.getClass().getMethod("getGuideConfig").invoke(guide);
        check(config.getClass().getMethod("getGuidePageType").invoke(config).equals(0)); assertions++;
        check(config.getClass().getMethod("getGuideMode").invoke(config).equals("0")); assertions++;
        check(config.getClass().getMethod("isVerifySwitch").invoke(config).equals(false)); assertions++;
        check(config.getClass().getMethod("isNewGuideSwitch").invoke(config).equals(true)); assertions++;
        check(((java.util.List<?>) guide.getClass().getMethod("getRiskDtoList").invoke(guide)).isEmpty()); assertions++;
        Object second = dispatch.invoke(adapter, "GUIDE", chain);
        check(second != guide); assertions++;
        Object state = dispatch.invoke(adapter, "STATE", chain);
        String expectedState = versionCode == 51762 ? "de.i" : "yd.k";
        String expectedFragment = "com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment";
        check(state.getClass().getName().equals(expectedState)); assertions++;
        Object preload = dispatch.invoke(adapter, "PRELOAD", chain);
        check(preload.getClass().getMethod("c").invoke(preload).getClass().getName().equals(expectedState)); assertions++;
        Object parsingChain = Proxy.newProxyInstance(loader, new Class<?>[]{chainType}, (proxy, method, values) -> {
            if (method.getName().equals("proceed")) return null;
            throw new AssertionError("Unexpected parsing chain operation: " + method.getName());
        });
        Object parsed = dispatch.invoke(adapter, "PARSED", parsingChain);
        check(parsed.getClass().getMethod("b").invoke(parsed).equals(false)); assertions++;
        // Its constructor reads application preferences; instantiate only in the real app process.
        check(((Constructor<?>) field(adapter, "fragment")).getDeclaringClass().getName().equals(expectedFragment)); assertions++;
        check(dispatch.invoke(adapter, "NULL", chain) == null); assertions++;
        check(dispatch.invoke(adapter, "NOOP", chain) == null); assertions++;
        check(dispatch.invoke(adapter, "FALSE", chain).equals(false)); assertions++;
        check(((Enum<?>) dispatch.invoke(adapter, "DEFAULT", chain)).name().equals("DEFAULT")); assertions++;
        check(dispatch.invoke(adapter, "UNIT", chain).getClass().getName().equals("kotlin.s")); assertions++;
        Class<?> vmClass = Class.forName("com.oplus.appdetail.modelv2.guide.viewmodel.RiskScanViewModel", true, loader);
        Object vm = vmClass.getDeclaredConstructor().newInstance();
        Object readyChain = Proxy.newProxyInstance(loader, new Class<?>[]{chainType}, (proxy, method, values) -> {
            if (method.getName().equals("getThisObject")) return vm;
            throw new AssertionError("Native ready started an original scanner: " + method.getName());
        });
        check(dispatch.invoke(adapter, "NATIVE_READY", readyChain) == null); assertions++;
        Object live = vmClass.getMethod("o").invoke(vm);
        Object readyValue = live.getClass().getMethod("getValue").invoke(live);
        check(readyValue.getClass().getName().equals("com.oplus.appdetail.model.guide.repository.riskScan.k")); assertions++;
        AtomicInteger called = new AtomicInteger();
        Object throwingChain = Proxy.newProxyInstance(loader, new Class<?>[]{chainType}, (proxy, method, values) -> {
            if (method.getName().equals("proceed")) {
                check(values.length == 1 && ((Object[]) values[0]).length == 1 && ((Object[]) values[0])[0] == null);
                called.incrementAndGet();
                throw new IllegalStateException("sentinel-install-failure");
            }
            throw new AssertionError("Original installation error was swallowed");
        });
        try {
            dispatch.invoke(adapter, "RESULT", throwingChain);
            throw new AssertionError("Original installation error was swallowed");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof IllegalStateException
                    && expected.getCause().getMessage().equals("sentinel-install-failure")); assertions++;
        }
        check(called.get() == 1); assertions++;
        System.out.println("DEVICE_PROBE_PASS versionCode=" + versionCode + " hooks=" + specs.length + " assertions=" + assertions);
        System.out.println("No Vector injection or end-to-end installation was exercised.");
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Probe assertion failed");
    }
    private static Object field(Object receiver, String name) throws ReflectiveOperationException {
        Field field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }
}
