package cn.lzy.gt7p.installerclean;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Populate the original PermissionAdapter with complete native row objects. */
final class PermissionDetails {
    static final String OWNED = "cn.lzy.gt7p.installerclean.permission_details";
    private final Constructor<?> row, heading;
    private final Method setLabel, setDescription, setIndex;

    PermissionDetails(ClassLoader loader, boolean old) throws Exception {
        Class<?> type = Class.forName(old ? "le.a" : "ge.a", false, loader);
        row = type.getDeclaredConstructor();
        row.setAccessible(true);
        heading = Class.forName(old ? "le.b" : "ge.b", false, loader).getDeclaredConstructor(int.class);
        setLabel = type.getDeclaredMethod("f", String.class);
        setDescription = type.getDeclaredMethod("e", String.class);
        setIndex = type.getDeclaredMethod("g", int.class);
    }

    static PermissionCatalog.Snapshot catalog(Context context, String[] names, String[] sensitive) {
        String fallback = "zh".equals(context.getResources().getConfiguration().getLocales().get(0).getLanguage())
                ? "系统未提供此权限的说明。" : "No permission description is available.";
        PackageManager manager = context.getPackageManager();
        return PermissionCatalog.build(names, sensitive, name -> {
            try {
                PermissionInfo info = manager.getPermissionInfo(name, 0);
                CharSequence label = info.loadLabel(manager), description = info.loadDescription(manager);
                return new PermissionCatalog.Metadata(label == null ? null : label.toString(),
                        description == null ? null : description.toString());
            } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
                return null;
            }
        }, fallback);
    }

    private Object row(PermissionCatalog.Entry entry, int index) throws Exception {
        Object result = row.newInstance();
        setLabel.invoke(result, entry.label());
        setDescription.invoke(result, entry.description());
        setIndex.invoke(result, index);
        return result;
    }

    List<Object> rows(Activity activity) throws Exception {
        Intent intent = activity.getIntent();
        if (intent == null || !intent.getBooleanExtra(OWNED, false)) return null;
        PermissionCatalog.Snapshot snapshot = catalog(activity, intent.getStringArrayExtra("local_permission_data"),
                intent.getStringArrayExtra("sensitive_permission_data"));
        List<Object> result = new ArrayList<>();
        // An unknown item must not be represented as a normal/safe classification.
        // Keep the native numbered rows, without group labels, if any metadata is missing.
        if (snapshot.hasFallback()) {
            int index = 1;
            for (PermissionCatalog.Entry entry : snapshot.entries()) result.add(row(entry, index++));
        } else {
            for (boolean sensitive : new boolean[]{true, false}) {
                int index = 1;
                for (PermissionCatalog.Entry entry : snapshot.entries()) {
                    if (entry.sensitive() != sensitive) continue;
                    if (index == 1) result.add(heading.newInstance(sensitive ? 0 : 1));
                    result.add(row(entry, index++));
                }
            }
        }
        return result;
    }
}
