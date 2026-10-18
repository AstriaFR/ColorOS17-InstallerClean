package cn.lzy.gt7p.installerclean;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.widget.TextView;
import android.widget.ImageView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Populate the original PermissionAdapter with complete native row objects. */
final class PermissionDetails {
    static final String OWNED = "cn.lzy.gt7p.installerclean.permission_details";
    private static final String HEADER_DATA = OWNED + ".headers";
    private final Constructor<?> row, heading;
    private final Method setLabel, setDescription, setIndex;
    private final Field headingTitle, headingIcon, adapterActivity;
    private final Map<Object, Map<Integer, PermissionCatalog.Category>> headers = new WeakHashMap<>();

    PermissionDetails(ClassLoader loader, boolean old) throws ReflectiveOperationException {
        Class<?> type = Class.forName(old ? "le.a" : "ge.a", false, loader);
        row = type.getDeclaredConstructor();
        row.setAccessible(true);
        heading = Class.forName(old ? "le.b" : "ge.b", false, loader).getDeclaredConstructor(int.class);
        heading.setAccessible(true);
        setLabel = type.getDeclaredMethod("f", String.class);
        setDescription = type.getDeclaredMethod("e", String.class);
        setIndex = type.getDeclaredMethod("g", int.class);
        setLabel.setAccessible(true);
        setDescription.setAccessible(true);
        setIndex.setAccessible(true);
        adapterActivity = Class.forName(old ? "ke.a" : "fe.a", false, loader).getDeclaredField("a");
        if (adapterActivity.getType() != Activity.class) throw new NoSuchFieldException("Permission adapter activity type");
        adapterActivity.setAccessible(true);
        headingTitle = Class.forName(old ? "ke.a$b" : "fe.a$b", false, loader).getDeclaredField("b");
        if (headingTitle.getType() != TextView.class) throw new NoSuchFieldException("Permission heading title type");
        headingTitle.setAccessible(true);
        headingIcon = headingTitle.getDeclaringClass().getDeclaredField("a");
        if (headingIcon.getType() != ImageView.class) throw new NoSuchFieldException("Permission heading icon type");
        headingIcon.setAccessible(true);
    }

    static PermissionCatalog.Snapshot catalog(Context context, String[] names, String[] sensitive) {
        String fallback = "zh".equals(context.getResources().getConfiguration().getLocales().get(0).getLanguage())
                ? "系统未提供此权限的说明。" : "No permission description is available.";
        PackageManager manager = context.getPackageManager();
        Map<String, Boolean> systemPackages = new HashMap<>();
        return PermissionCatalog.build(names, sensitive, name -> {
            try {
                PermissionInfo info = manager.getPermissionInfo(name, 0);
                CharSequence label = info.loadLabel(manager), description = info.loadDescription(manager);
                boolean system = "android".equals(info.packageName);
                if (!system && info.packageName != null) {
                    Boolean cached = systemPackages.get(info.packageName);
                    if (cached == null) {
                        try {
                            ApplicationInfo owner = manager.getApplicationInfo(info.packageName, 0);
                            cached = (owner.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) { cached = false; }
                        systemPackages.put(info.packageName, cached);
                    }
                    system = cached;
                }
                return new PermissionCatalog.Metadata(label == null ? null : label.toString(),
                        description == null ? null : description.toString(), system,
                        (info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS);
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

    List<Object> rows(Object adapter, Activity activity) throws Exception {
        Intent intent = activity.getIntent();
        if (intent == null || !intent.getBooleanExtra(OWNED, false)) return null;
        PermissionCatalog.Snapshot snapshot = catalog(activity, intent.getStringArrayExtra("local_permission_data"),
                intent.getStringArrayExtra("sensitive_permission_data"));
        List<Object> result = new ArrayList<>();
        Map<Integer, PermissionCatalog.Category> positions = new HashMap<>();
        for (PermissionCatalog.Category category : PermissionCatalog.Category.values()) {
            int index = 1;
            for (PermissionCatalog.Entry entry : snapshot.entries()) {
                if (entry.category() != category) continue;
                if (index == 1) {
                    positions.put(result.size(), category);
                    // Reuse native heading view types; type 2 is reserved for permission rows.
                    result.add(heading.newInstance(result.isEmpty() ? 0 : 1));
                }
                result.add(row(entry, index++));
            }
        }
        synchronized (headers) { headers.put(adapter, positions); }
        // Strings in the host Intent survive module classloader replacement.
        String[] saved = new String[result.size()];
        for (Map.Entry<Integer, PermissionCatalog.Category> item : positions.entrySet()) saved[item.getKey()] = item.getValue().name();
        intent.putExtra(HEADER_DATA, saved);
        return result;
    }

    void bindHeader(Object adapter, Object holder, int position) throws Exception {
        boolean cached;
        synchronized (headers) { cached = headers.containsKey(adapter); }
        if (!cached && adapterActivity.getDeclaringClass().isInstance(adapter)) {
            Activity activity = (Activity) adapterActivity.get(adapter);
            Intent intent = activity == null ? null : activity.getIntent();
            if (intent != null && intent.getBooleanExtra(OWNED, false)) {
                String[] saved = intent.getStringArrayExtra(HEADER_DATA);
                if (saved == null) rows(adapter, activity); // Migrate an already-open page from an older build.
                else {
                    Map<Integer, PermissionCatalog.Category> positions = new HashMap<>();
                    for (int i = 0; i < saved.length; i++) {
                        if (saved[i] == null) continue;
                        try { positions.put(i, PermissionCatalog.Category.valueOf(saved[i])); }
                        catch (IllegalArgumentException unknownCategory) { /* Preserve native binding. */ }
                    }
                    synchronized (headers) { headers.put(adapter, positions); }
                }
            }
        }
        PermissionCatalog.Category category;
        synchronized (headers) {
            Map<Integer, PermissionCatalog.Category> positions = headers.get(adapter);
            category = positions == null ? null : positions.get(position);
        }
        if (category == null || !headingTitle.getDeclaringClass().isInstance(holder)) return;
        TextView title = (TextView) headingTitle.get(holder);
        boolean zh = "zh".equals(title.getResources().getConfiguration().getLocales().get(0).getLanguage());
        title.setText(switch (category) {
            case SENSITIVE -> zh ? "敏感权限" : "Sensitive permissions";
            case NORMAL -> zh ? "普通权限" : "Normal permissions";
            case CUSTOM -> zh ? "自定义权限" : "Custom permissions";
        });
        ImageView icon = (ImageView) headingIcon.get(holder);
        int drawable = title.getResources().getIdentifier(category == PermissionCatalog.Category.SENSITIVE
                ? "detail_privacy_permission" : "detail_normal_permission", "drawable", Profile.PACKAGE);
        if (drawable != 0) icon.setImageResource(drawable);
    }
}
