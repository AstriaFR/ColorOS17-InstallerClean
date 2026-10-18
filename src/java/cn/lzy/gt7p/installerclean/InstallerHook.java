package cn.lzy.gt7p.installerclean;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Version-selected rules. No device, Android version, firmware, hash or split APK gates. */
public final class InstallerHook extends XposedModule {
    private static final String TAG = "GT7PInstallerClean";
    private final List<HookHandle> handles = new ArrayList<>();
    private volatile boolean active;
    private boolean attempted;

    private void report(String message) {
        log(Log.INFO, TAG, message);
        Log.i(TAG, message);
    }

    @Override public synchronized void onPackageReady(PackageReadyParam param) {
        if (attempted || !param.isFirstPackage()) return;
        if (Profile.NATIVE_PACKAGE.equals(param.getPackageName())) {
            attempted = true;
            try { NativeRouter.install(this, param.getClassLoader(), Compatibility.versionCode(param.getApplicationInfo())); }
            catch (Throwable failure) { log(Log.ERROR, TAG, "DISABLED native router", failure); }
            return;
        }
        if (!Profile.PACKAGE.equals(param.getPackageName())) return;
        attempted = true;
        try {
            long code = Compatibility.versionCode(param.getApplicationInfo());
            Profile.Rule rule = Profile.select(code);
            if (rule == null) { report("UNSUPPORTED installer versionCode=" + code + "; original behavior retained"); return; }
            Adapter adapter = new Adapter(param.getClassLoader(), rule);
            List<Method> targets = new ArrayList<>();
            // Resolve every method, return type and callback dependency before mutating anything.
            for (String[] spec : rule.hooks) {
                targets.add(adapter.method(spec[1], spec[2], spec[3], Arrays.copyOfRange(spec, 4, spec.length)));
            }
            List<Method> callers = new ArrayList<>();
            for (String[] caller : rule.deopt) {
                boolean found = false;
                for (Method method : adapter.type(caller[0]).getDeclaredMethods()) {
                    if (method.getName().equals(caller[1])) { callers.add(method); found = true; }
                }
                if (!found) throw new NoSuchMethodException(caller[0] + "#" + caller[1]);
            }
            for (int index = 0; index < rule.hooks.length; index++) {
                String[] spec = rule.hooks[index];
                Method target = targets.get(index);
                String id = "installer:" + rule.versionCode + ":" + index;
                handles.add(hook(target).setId(id).intercept(chain -> {
                    if (!active) return chain.proceed();
                    return adapter.dispatch(spec[0], chain);
                }));
            }
            // Deopt exact callers so existing AOT inlining cannot silently bypass small hooks.
            for (Method target : targets) deoptimize(target);
            for (Method caller : callers) deoptimize(caller);
            active = true;
            report("ACTIVE appdetail=" + rule.versionName + " versionCode=" + code + " hooks=" + handles.size());
        } catch (Throwable failure) {
            active = false;
            for (int i = handles.size() - 1; i >= 0; i--) {
                try { handles.get(i).unhook(); } catch (Throwable ignored) { }
            }
            handles.clear();
            log(Log.ERROR, TAG, "DISABLED: preflight/install failed; original behavior retained", failure);
            Log.e(TAG, "DISABLED: preflight/install failed", failure);
        }
    }

    private static final class Adapter {
        private final ClassLoader loader;
        private final Constructor<?> guide, state, preload, parsed, fragment;
        private final Method getConfig, setMode, setPageType, setScanTime, setWarning, setVerify,
                setNewGuide, setRisks, getCardsLiveData, getMarketLiveData, getDetectLiveData, postValue;
        private final Object unit, defaultDialog;
        private final Method scanStart, scanLiveData, setValue, nativeButton, buttonListener,
                bindBottom, findFragment, fragmentActivity, navigate;
        private final Constructor<?> installButton;
        private final Object scanReady;
        private final PermissionPanel permissions;

        Adapter(ClassLoader loader, Profile.Rule rule) throws Exception {
            this.loader = loader;
            String domain = "com.heytap.cdo.security.domain.safeguide.";
            String resultModel = "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel";
            guide = constructor(domain + "GuideResult");
            state = constructor(rule.state, domain + "GuideResult"); // NoGuide state, not a scan verdict.
            preload = constructor("com.oplus.appdetail.modelv2.parsing.reository.c", rule.stateBase,
                    "com.heytap.cdo.security.domain.expgray.ConfigResult", "java.lang.String");
            parsed = constructor("com.oplus.appdetail.modelv2.parsing.b$j", "boolean");
            fragment = constructor(rule.fragment); // Original full-page post-scan fragment.
            boolean old = rule.versionCode == 51762;
            permissions = new PermissionPanel(loader, old);
            String fetcher = "com.oplus.appdetail.modelv2.guide.helper." + (old ? "h" : "i");
            String bottomData = old ? "ue.c" : "pe.c";
            String presenter = "com.oplus.appdetail.modelv2.guide.presenter.GuideBottomViewPresenter";
            String dialog = "com.oplus.appdetail.modelv2.riskDialog.DialogHelper";
            scanStart = method(rule.fragment, old ? "K" : "L", "void");
            scanLiveData = method("com.oplus.appdetail.modelv2.guide.viewmodel.RiskScanViewModel", "o", "androidx.lifecycle.MutableLiveData");
            setValue = method("androidx.lifecycle.MutableLiveData", "setValue", "void", "java.lang.Object");
            Field ready = type("com.oplus.appdetail.model.guide.repository.riskScan.k").getDeclaredField("a");
            ready.setAccessible(true);
            scanReady = ready.get(null);
            nativeButton = method(presenter, "c", bottomData, "boolean");
            buttonListener = method(bottomData, "b", "android.view.View$OnClickListener");
            installButton = constructor(bottomData + "$b", "java.lang.String", "android.view.View$OnClickListener", "boolean");
            bindBottom = method("com.oplus.appdetail.modelv2.guide.view.GuideBottomView", old ? "o" : "j", "void",
                    "java.util.List", old ? "ue.a" : "pe.a", "boolean");
            findFragment = method(dialog, "e", "androidx.fragment.app.Fragment", "android.view.View");
            fragmentActivity = method("androidx.fragment.app.Fragment", "getActivity", "androidx.fragment.app.FragmentActivity");
            navigate = method("com.oplus.appdetail.modelv2.helper.a", "d", "void", "android.app.Activity", fetcher);
            getConfig = method(domain + "GuideResult", "getGuideConfig", domain + "GuideConfig");
            setRisks = method(domain + "GuideResult", "setRiskDtoList", "void", "java.util.List");
            setMode = method(domain + "GuideConfig", "setGuideMode", "void", "java.lang.String");
            setPageType = method(domain + "GuideConfig", "setGuidePageType", "void", "int");
            setScanTime = method(domain + "GuideConfig", "setScanTime", "void", "java.lang.String");
            setWarning = method(domain + "GuideConfig", "setShowWarning", "void", "java.lang.String");
            setVerify = method(domain + "GuideConfig", "setVerifySwitch", "void", "boolean");
            setNewGuide = method(domain + "GuideConfig", "setNewGuideSwitch", "void", "boolean");
            getCardsLiveData = method(resultModel, rule.cardsGetter, "androidx.lifecycle.MutableLiveData");
            getMarketLiveData = method(resultModel, rule.marketGetter, "androidx.lifecycle.MutableLiveData");
            getDetectLiveData = method(resultModel, rule.detectGetter, "androidx.lifecycle.MutableLiveData");
            postValue = method("androidx.lifecycle.MutableLiveData", "postValue", "void", "java.lang.Object");
            Field unitField = type("kotlin.s").getDeclaredField("a");
            unitField.setAccessible(true);
            unit = unitField.get(null);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object value = Enum.valueOf((Class) type("com.oplus.appdetail.model.install.util.ResultPageDialogType"), "DEFAULT");
            defaultDialog = value;
        }

        Class<?> type(String name) throws ClassNotFoundException {
            switch (name) {
                case "void": return void.class;
                case "boolean": return boolean.class;
                case "int": return int.class;
                case "long": return long.class;
                default: return Class.forName(name, false, loader);
            }
        }

        private Class<?>[] types(String... names) throws ClassNotFoundException {
            Class<?>[] result = new Class<?>[names.length];
            for (int i = 0; i < names.length; i++) result[i] = type(names[i]);
            return result;
        }

        Method method(String owner, String name, String result, String... parameters) throws Exception {
            Method method = type(owner).getDeclaredMethod(name, types(parameters));
            if (!method.getReturnType().equals(type(result))) throw new NoSuchMethodException(owner + "#" + name + " return type");
            method.setAccessible(true);
            return method;
        }

        private Constructor<?> constructor(String owner, String... parameters) throws Exception {
            Constructor<?> result = type(owner).getDeclaredConstructor(types(parameters));
            result.setAccessible(true);
            return result;
        }

        private static Object call(Method method, Object receiver, Object... args) throws Throwable {
            try { return method.invoke(receiver, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }

        private Object localGuide() throws Throwable {
            Object result = guide.newInstance();
            Object config = call(getConfig, result);
            call(setMode, config, "0");
            call(setPageType, config, 0);
            call(setScanTime, config, "0");
            call(setWarning, config, "0");
            call(setVerify, config, false);
            // Legacy UI still reads this flag when returning to PackageInstaller.
            // true => oplus_extra_execute_old_process=false; do not restart its legacy scanner.
            call(setNewGuide, config, true);
            call(setRisks, result, Collections.emptyList());
            return result;
        }

        Object dispatch(String action, Chain chain) throws Throwable {
            switch (action) {
                // Suspend functions return final values directly. Never resume their continuation as well.
                case "PRELOAD": return preload.newInstance(state.newInstance(localGuide()), null, "");
                case "PARSED": return parsed.newInstance(false); // Reached only after real APK staging/parsing.
                case "FRAGMENT": return fragment.newInstance();
                case "STATE": return state.newInstance(localGuide());
                case "GUIDE": return localGuide();
                case "NULL": case "NOOP": return null;
                case "UNIT": return unit;
                case "FALSE": return false;
                case "DEFAULT": return defaultDialog;
                case "NATIVE_SCAN_START":
                    return call(scanStart, chain.getThisObject());
                case "NATIVE_READY":
                    call(setValue, call(scanLiveData, chain.getThisObject()), scanReady);
                    Log.i(TAG, "UI appdetail-native-ready");
                    return null;
                case "PERMISSIONS":
                    permissions.bind((ViewGroup) chain.getThisObject(), chain.getArg(1));
                    return null;
                case "NATIVE_BOTTOM": {
                    View bottom = (View) chain.getArg(0);
                    Object original = call(nativeButton, chain.getThisObject(), true);
                    int textId = bottom.getResources().getIdentifier("install", "string", Profile.PACKAGE);
                    Object button = installButton.newInstance(bottom.getResources().getString(textId), call(buttonListener, original), true);
                    call(bindBottom, bottom, Collections.singletonList(button), null, true);
                    return null;
                }
                case "NATIVE_INSTALL": {
                    Object page = call(findFragment, chain.getThisObject(), chain.getArg(0));
                    if (page == null) return false;
                    Object activity = call(fragmentActivity, page);
                    if (activity == null) return false;
                    call(navigate, null, activity, chain.getArg(1));
                    Log.i(TAG, "INSTALL appdetail-native-confirmed");
                    return true;
                }
                case "FINISH_NOTE": {
                    Object result = chain.proceed();
                    View panel = (View) chain.getThisObject();
                    int id = panel.getResources().getIdentifier("process_risk_detail", "id", Profile.PACKAGE);
                    View note = panel.findViewById(id);
                    if (note != null) note.setVisibility(View.GONE);
                    return result;
                }
                case "HIDE_SCAN_NOTE": {
                    ((View) chain.getThisObject()).setVisibility(View.GONE);
                    return null;
                }
                case "RESULT": {
                    // Keep original completion/error bookkeeping. Drop the optional recommendation response.
                    chain.proceed(new Object[]{null});
                    Object receiver = chain.getThisObject();
                    // 5.17 observes CardDto; 5.23 observes List<CardDto>. Both accept null.
                    call(postValue, call(getCardsLiveData, receiver), (Object) null);
                    call(postValue, call(getMarketLiveData, receiver), false);
                    call(postValue, call(getDetectLiveData, receiver), false);
                    return null;
                }
                default: throw new IllegalArgumentException("Unknown action: " + action);
            }
        }
    }
}
