package cn.lzy.gt7p.installerclean;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** Local APK permission metadata, rendered in vendor views and its original detail Activity. */
final class PermissionPanel {
    private final Constructor<?> resultView;
    private final Class<?> details;
    private final Method jumpParam, requiredPermissions, apkPath, classify, sensitive, normal,
            background, radius;

    PermissionPanel(ClassLoader loader, boolean old) throws Exception {
        String fetcher = "com.oplus.appdetail.modelv2.guide.helper." + (old ? "h" : "i");
        String param = "com.oplus.appdetail.model.guide.repository.ExtJumpParam";
        resultView = Class.forName("com.oplus.appdetail.modelv2.guide.view.GuideScanResultView", false, loader)
                .getDeclaredConstructor(Context.class);
        details = Class.forName("com.oplus.appdetail.model.permission.ui.PermissionActivity", false, loader);
        jumpParam = method(loader, fetcher, "h");
        requiredPermissions = method(loader, param, "getAppRequirePermissions");
        apkPath = method(loader, param, "getApkFilePath");
        String util = old ? "ld.c" : "gd.c";
        classify = method(loader, util, "e", String[].class);
        sensitive = method(loader, util, "c");
        normal = method(loader, util, "b");
        String card = "com.oplus.appdetail.base.view.RoundLinearLayout";
        background = method(loader, card, "setCardBackgroundColor", int.class);
        radius = method(loader, card, "setRadius", int.class);
    }

    private static Method method(ClassLoader loader, String owner, String name, Class<?>... args) throws Exception {
        Method result = Class.forName(owner, false, loader).getDeclaredMethod(name, args);
        result.setAccessible(true);
        return result;
    }

    private static Object call(Method method, Object receiver, Object... args) throws Throwable {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }

    private static int id(Resources resources, String name, String kind) {
        int value = resources.getIdentifier(name, kind, Profile.PACKAGE);
        if (value == 0) throw new Resources.NotFoundException(kind + "/" + name);
        return value;
    }

    private static int color(Context context, String attribute, int fallback) {
        TypedValue value = new TypedValue();
        int resource = context.getResources().getIdentifier(attribute, "attr", Profile.PACKAGE);
        if (resource == 0 || !context.getTheme().resolveAttribute(resource, value, true)) return fallback;
        return value.resourceId == 0 ? value.data : context.getResources().getColor(value.resourceId, context.getTheme());
    }

    void bind(ViewGroup host, Object fetcher) throws Throwable {
        Context context = host.getContext();
        Object param = call(jumpParam, fetcher);
        String[] permissions = (String[]) call(requiredPermissions, param);
        if (permissions == null) {
            String path = (String) call(apkPath, param);
            PackageInfo info = path == null ? null : context.getPackageManager().getPackageArchiveInfo(path, PackageManager.GET_PERMISSIONS);
            if (info == null) { host.setVisibility(View.GONE); return; }
            permissions = info.requestedPermissions == null ? new String[0] : info.requestedPermissions;
        }
        final String[] local = permissions.clone();
        call(classify, null, (Object) local);
        String[] sensitiveNames = ((List<?>) call(sensitive, null)).toArray(new String[0]);
        String[] normalNames = ((List<?>) call(normal, null)).toArray(new String[0]);
        Resources resources = host.getResources();
        View panel = (View) resultView.newInstance(context);
        TextView title = panel.findViewById(id(resources, "tv_result_title", "id"));
        TextView subtitle = panel.findViewById(id(resources, "tv_result_desc", "id"));
        title.setText(id(resources, "ad_app_permission", "string"));
        title.setTextColor(color(context, "couiColorLabelPrimary", title.getCurrentTextColor()));
        String count = resources.getString(id(resources, "text_normal_permission", "string"), local.length);
        String detail = resources.getString(id(resources, "sensitive_permission_detail_text", "string"));
        if (sensitiveNames.length != 0) count += " · " + resources.getString(id(resources, "text_sensitive_permission", "string"), sensitiveNames.length);
        subtitle.setText(count + (local.length == 0 ? "" : " · " + detail));
        subtitle.setTextColor(color(context, "couiColorLabelSecondary", subtitle.getCurrentTextColor()));
        View divider = panel.findViewById(id(resources, "divider_line", "id"));
        divider.setVisibility(View.GONE);
        View card = panel.findViewById(id(resources, "scan_result_container", "id"));
        card.setPadding(card.getPaddingLeft(), card.getPaddingTop(), card.getPaddingRight(),
                card.getPaddingBottom() + Math.round(16f * resources.getDisplayMetrics().density));
        call(background, card, color(context, "couiColorContainer4", 0xfff0f0f0));
        call(radius, card, Math.round(20f * resources.getDisplayMetrics().density));
        if (local.length != 0) {
            card.setOnClickListener(view -> {
                Context target = view.getContext();
                Intent intent = new Intent(target, details);
                intent.putExtra("local_permission_data", local);
                intent.putExtra("sensitive_permission_data", sensitiveNames);
                intent.putExtra("normal_permission_data", normalNames);
                if (!(target instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                target.startActivity(intent);
            });
            card.setContentDescription(title.getText() + ", " + subtitle.getText());
        }
        host.removeAllViews();
        host.addView(panel, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.setVisibility(View.VISIBLE);
    }
}
