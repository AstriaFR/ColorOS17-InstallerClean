package cn.lzy.gt7p.installerclean;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** Host-side Android fixtures; no phone or actual APK installation. */
public final class AndroidRegressionTest {
    private static int assertions;
    private static void check(boolean result) { if (!result) throw new AssertionError(); assertions++; }
    private static android.app.Activity activity() {
        return new android.app.Activity(new android.content.Intent().putExtra(PermissionDetails.OWNED,true)
                .putExtra("local_permission_data",new String[]{"android.permission.CAMERA","android.permission.INTERNET","custom.permission.X"})
                .putExtra("sensitive_permission_data",new String[]{"android.permission.CAMERA"}));
    }
    public static void main(String[] args) throws Exception {
        ClassLoader loader = AndroidRegressionTest.class.getClassLoader();
        android.app.Activity activity = activity();
        ke.a adapter = new ke.a(activity);
        PermissionDetails first = new PermissionDetails(loader,true);
        check(first.rows(adapter,activity).size()==6); // Private row setters remain callable.
        ke.a.b holder = new ke.a.b();
        first.bindHeader(adapter,holder,4);
        check(holder.b.text.equals("自定义权限"));
        holder.b.setText("普通权限");
        new PermissionDetails(loader,true).bindHeader(adapter,holder,4);
        check(holder.b.text.equals("自定义权限")); // Saved host data survives a new generation.
        activity.getIntent().putExtra(PermissionDetails.OWNED+".headers",(String[])null);
        holder.b.setText("普通权限");
        new PermissionDetails(loader,true).bindHeader(adapter,holder,4);
        check(holder.b.text.equals("自定义权限")); // Old page without saved data is reconstructed.
        ke.a unowned = new ke.a(new android.app.Activity(new android.content.Intent()));
        holder.b.setText("Original");
        new PermissionDetails(loader,true).bindHeader(unowned,holder,0);
        check(holder.b.text.equals("Original"));
        try (URLClassLoader privateHeader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},loader) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if (name.equals("le.b")) {
                    Class<?> result=findLoadedClass(name);if(result==null)result=findClass(name);
                    if(resolve)resolveClass(result);return result;
                }
                return super.loadClass(name,resolve);
            }
        }) {
            android.app.Activity second = activity();
            check(new PermissionDetails(privateHeader,true).rows(new ke.a(second),second).size()==6);
        }
        System.out.println("ANDROID_FIXTURE_PASS assertions="+assertions);
    }
}
