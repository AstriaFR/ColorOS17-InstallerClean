package cn.lzy.gt7p.installerclean;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

/** Structural feature probes; target versions are recorded only for diagnostics. */
public final class InstallerHook extends XposedModule {
    private static final String TAG = "GT7PInstallerClean";
    private final List<HookHandle> handles = new ArrayList<>();
    private volatile boolean active;
    private boolean attempted;
    private ReloadState.Target target;
    private Handler statusHandler;
    private Runnable pendingStatus;

    private void report(String message) {
        String identified = BuildInfo.identity() + " " + message;
        log(Log.WARN, TAG, identified);
        Log.w(TAG, identified);
    }

    private void reportActive(String message) {
        report(message);
        // ColorOS drops the tail of large hook-registration log bursts. Repeat once after it.
        Looper main = Looper.getMainLooper();
        if (main == null) return;
        statusHandler = new Handler(main);
        pendingStatus = () -> { if (active) report(message + " CONFIRMED"); };
        statusHandler.postDelayed(pendingStatus, 2500L);
    }

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        report("LOADED process=" + param.getProcessName());
    }

    @Override public synchronized boolean onHotReloading(HotReloadingParam param) {
        // A target that has not reached PackageReady must finish its normal cold initialization.
        if (target == null) return false;
        if (statusHandler != null && pendingStatus != null) statusHandler.removeCallbacks(pendingStatus);
        param.setSavedInstanceState(ReloadState.save(target));
        report("RELOAD_HANDOFF package=" + target.packageName());
        return true;
    }

    @Override public synchronized void onHotReloaded(HotReloadedParam param) {
        target = ReloadState.restore(param.getSavedInstanceState());
        if (!Profile.PACKAGE.equals(target.packageName()) && !Profile.NATIVE_PACKAGE.equals(target.packageName())) {
            throw new IllegalArgumentException("Unexpected reload target: " + target.packageName());
        }
        attempted = true;
        // PackageReady is not replayed on hot reload. Resolve the saved host anew and reinstall.
        initialize(target, param.getOldHookHandles());
        if (!active) throw new IllegalStateException(BuildInfo.identity() + " reload did not activate rules");
        report("RELOADED process=" + param.getProcessName());
    }

    private static void retire(List<HookHandle> oldHandles) {
        for (HookHandle handle : oldHandles) handle.unhook();
    }

    @Override public synchronized void onPackageReady(PackageReadyParam param) {
        if (attempted || !param.isFirstPackage()) return;
        if (!Profile.NATIVE_PACKAGE.equals(param.getPackageName()) && !Profile.PACKAGE.equals(param.getPackageName())) return;
        attempted = true;
        long observedVersion = 0;
        try { observedVersion = Compatibility.versionCode(param.getApplicationInfo()); }
        catch (ReflectiveOperationException unavailable) { report("INFO installer version unavailable; structural probing continues"); }
        target = new ReloadState.Target(param.getPackageName(), param.getClassLoader(), observedVersion);
        initialize(target, Collections.emptyList());
    }

    private void initialize(ReloadState.Target host, List<HookHandle> oldHandles) {
        try {
            if (Profile.NATIVE_PACKAGE.equals(host.packageName())) {
                NativeRouter.Plan plan = NativeRouter.prepare(host.loader());
                retire(oldHandles);
                handles.add(NativeRouter.install(this, plan));
                active = true;
                reportActive("ACTIVE native observedVersion=" + host.version() + " selection=structure hooks=1");
                return;
            }
            Map<Feature, StructuralProbe.Prepared> prepared = new EnumMap<>(Feature.class);
            Map<Feature, Adapter> adapters = new EnumMap<>(Feature.class);
            for (Feature feature : Feature.values()) {
                StructuralProbe.Prepared flow = prepared.get(Feature.FLOW);
                if (feature == Feature.PERMISSIONS && flow == null) {
                    report("PROBE feature=PERMISSIONS SKIP dependency=FLOW");
                    continue;
                }
                StructuralProbe.Decision decision = StructuralProbe.select(host.loader(), feature,
                        feature == Feature.PERMISSIONS ? flow.rule() : null);
                if (decision.selected() == null) {
                    report("PROBE feature=" + feature + (decision.ambiguous() ? " AMBIGUOUS" : " NO_MATCH")
                            + " reasons=" + decision.reasons());
                    continue;
                }
                StructuralProbe.Prepared candidate = decision.selected();
                try {
                    adapters.put(feature, new Adapter(host.loader(), candidate.rule(), feature));
                    prepared.put(feature, candidate);
                    report("PROBE feature=" + feature + " MATCH template=" + candidate.rule().id + " hooks=" + candidate.targets().size());
                } catch (ReflectiveOperationException | LinkageError | SecurityException failure) {
                    report("PROBE feature=" + feature + " SKIP callback=" + failure);
                }
            }
            // No old hook is removed until all feature probes/callback dependencies are ready.
            retire(oldHandles);
            EnumSet<Feature> enabledFeatures = EnumSet.noneOf(Feature.class);
            for (Feature feature : Feature.values()) {
                StructuralProbe.Prepared candidate = prepared.get(feature);
                if (candidate == null || feature == Feature.PERMISSIONS && !enabledFeatures.contains(Feature.FLOW)) continue;
                Adapter adapter = adapters.get(feature);
                List<HookHandle> groupHandles = new ArrayList<>();
                AtomicBoolean enabled = new AtomicBoolean(false);
                try {
                    for (int index = 0; index < candidate.group().hooks.length; index++) {
                        String[] spec = candidate.group().hooks[index];
                        Method method = candidate.targets().get(index);
                        String id = "installer:" + feature + ":" + String.join(":", spec);
                        groupHandles.add(hook(method).setId(id).intercept(chain -> {
                            if (!enabled.get()) return chain.proceed();
                            return adapter.dispatch(spec[0], chain);
                        }));
                    }
                    for (Method method : candidate.targets()) deoptimize(method);
                    for (Executable caller : candidate.callers()) deoptimize(caller);
                    handles.addAll(groupHandles);
                    enabled.set(true);
                    enabledFeatures.add(feature);
                } catch (Throwable failure) {
                    for (HookHandle handle : groupHandles) { try { handle.unhook(); } catch (Throwable ignored) { } }
                    log(Log.ERROR, TAG, BuildInfo.identity() + " feature=" + feature + " ROLLED_BACK", failure);
                }
            }
            active = !handles.isEmpty();
            String status = "observedVersion=" + host.version() + " selection=structure features=" + enabledFeatures + " hooks=" + handles.size();
            if (active) reportActive("ACTIVE " + status); else report("INACTIVE " + status);
        } catch (Throwable failure) {
            active = false;
            for (int i = handles.size() - 1; i >= 0; i--) {
                try { handles.get(i).unhook(); } catch (Throwable ignored) { }
            }
            handles.clear();
            log(Log.ERROR, TAG, BuildInfo.identity() + " DISABLED: structural/native preflight failed", failure);
            Log.e(TAG, BuildInfo.identity() + " DISABLED: structural/native preflight failed", failure);
        }
    }

    private static final class Adapter {
        private final ClassLoader loader;
        private Constructor<?> guide, state, preload, parsed, fragment;
        private Method getConfig, setMode, setPageType, setScanTime, setWarning, setVerify,
                setNewGuide, setRisks, getCardsLiveData, getMarketLiveData, getDetectLiveData, postValue;
        private Field unit, defaultDialog;
        private Method scanStart, scanLiveData, setValue, nativeButton, buttonListener,
                bindBottom, findFragment, fragmentActivity, navigate;
        private Constructor<?> installButton;
        private Field scanReady;
        private PermissionPanel permissions;
        private PermissionDetails permissionDetails;
        private FinishButtons finishButtons;

        Adapter(ClassLoader loader, Profile.Rule rule, Feature feature) throws ReflectiveOperationException {
            this.loader = loader;
            boolean old = rule.legacy;
            if (feature == Feature.FINISH_OPEN) {
                finishButtons = new FinishButtons(loader, old);
                return;
            }
            if (feature == Feature.PERMISSIONS) {
                permissions = new PermissionPanel(loader, old);
                permissionDetails = new PermissionDetails(loader, old);
                return;
            }
            if (feature == Feature.RESULT_DIALOGS) {
                defaultDialog = field("com.oplus.appdetail.model.install.util.ResultPageDialogType", "DEFAULT");
                return;
            }
            if (feature == Feature.RESULT) {
                String model = "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel";
                getCardsLiveData = method(model, rule.cardsGetter, "androidx.lifecycle.MutableLiveData");
                getMarketLiveData = method(model, rule.marketGetter, "androidx.lifecycle.MutableLiveData");
                getDetectLiveData = method(model, rule.detectGetter, "androidx.lifecycle.MutableLiveData");
                postValue = method("androidx.lifecycle.MutableLiveData", "postValue", "void", "java.lang.Object");
                unit = field("kotlin.s", "a");
                return;
            }
            if (feature != Feature.FLOW) return;
            String domain = "com.heytap.cdo.security.domain.safeguide.";
            guide = constructor(domain + "GuideResult");
            state = constructor(rule.state, domain + "GuideResult");
            preload = constructor("com.oplus.appdetail.modelv2.parsing.reository.c", rule.stateBase,
                    "com.heytap.cdo.security.domain.expgray.ConfigResult", "java.lang.String");
            parsed = constructor("com.oplus.appdetail.modelv2.parsing.b$j", "boolean");
            fragment = constructor(rule.fragment);
            String fetcher = "com.oplus.appdetail.modelv2.guide.helper." + (old ? "h" : "i");
            String bottomData = old ? "ue.c" : "pe.c";
            String presenter = "com.oplus.appdetail.modelv2.guide.presenter.GuideBottomViewPresenter";
            String dialog = "com.oplus.appdetail.modelv2.riskDialog.DialogHelper";
            scanStart = method(rule.fragment, old ? "K" : "L", "void");
            scanLiveData = method("com.oplus.appdetail.modelv2.guide.viewmodel.RiskScanViewModel", "o", "androidx.lifecycle.MutableLiveData");
            setValue = method("androidx.lifecycle.MutableLiveData", "setValue", "void", "java.lang.Object");
            scanReady = field("com.oplus.appdetail.model.guide.repository.riskScan.k", "a");
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
        }

        private Field field(String owner, String name) throws ReflectiveOperationException {
            Field result = type(owner).getDeclaredField(name);
            result.setAccessible(true);
            return result;
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

        Method method(String owner, String name, String result, String... parameters) throws ReflectiveOperationException {
            Method method = type(owner).getDeclaredMethod(name, types(parameters));
            if (!method.getReturnType().equals(type(result))) throw new NoSuchMethodException(owner + "#" + name + " return type");
            method.setAccessible(true);
            return method;
        }

        private Constructor<?> constructor(String owner, String... parameters) throws ReflectiveOperationException {
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
                case "PARSED": return ParsingPolicy.choose(chain::proceed, () -> parsed.newInstance(false));
                case "FRAGMENT": return fragment.newInstance();
                case "STATE": return state.newInstance(localGuide());
                case "GUIDE": return localGuide();
                case "NULL": case "NOOP": return null;
                case "UNIT": return unit.get(null);
                case "FALSE": return false;
                case "DEFAULT": return defaultDialog.get(null);
                case "NATIVE_SCAN_START":
                    return call(scanStart, chain.getThisObject());
                case "NATIVE_READY":
                    call(setValue, call(scanLiveData, chain.getThisObject()), scanReady.get(null));
                    Log.i(TAG, "UI appdetail-native-ready");
                    return null;
                case "PERMISSIONS":
                    permissions.bind((ViewGroup) chain.getThisObject(), chain.getArg(1));
                    return null;
                case "PERMISSION_DETAILS": {
                    List<Object> rows = permissionDetails.rows(chain.getThisObject(), (android.app.Activity) chain.getArg(0));
                    return rows == null ? chain.proceed() : rows;
                }
                case "PERMISSION_HEADER": {
                    Object result = chain.proceed();
                    permissionDetails.bindHeader(chain.getThisObject(), chain.getArg(0), (Integer) chain.getArg(1));
                    return result;
                }
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
                case "FINISH_OPEN": {
                    Object original = chain.proceed();
                    try { return finishButtons.restore(chain.getThisObject(), original); }
                    catch (ReflectiveOperationException failure) {
                        Log.w(TAG, "Native finish actions unavailable", failure);
                        return original;
                    }
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
