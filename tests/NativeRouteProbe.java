package cn.lzy.gt7p.installerclean;
import dalvik.system.PathClassLoader;
import java.lang.reflect.Method;
public final class NativeRouteProbe {
    public static void main(String[] args) throws Throwable {
        PathClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Class<?> router = Class.forName("cn.lzy.gt7p.installerclean.NativeRouter", true, loader);
        Method policy = router.getDeclaredMethod("entryResult", boolean.class, long.class, int.class);
        policy.setAccessible(true);
        int checks=0;
        for (long code : new long[]{-1,51762,52302,99999}) {
            for (boolean installing : new boolean[]{false,true}) {
                for (int original=0; original<=13; original++) {
                    boolean change=installing && (code==51762 || code==52302) && (original==1 || original==11);
                    check(policy.invoke(null,installing,code,original).equals(change ? 0 : original)); checks++;
                }
            }
        }
        Method resolver=router.getDeclaredMethod("method",ClassLoader.class,String.class,String.class,Class.class,Class[].class);
        resolver.setAccessible(true);
        resolver.invoke(null,loader,"m1.c","k",int.class,new Class[]{android.content.Context.class,boolean.class,String.class}); checks++;
        resolver.invoke(null,loader,"com.android.packageinstaller.InstallStart","onCreate",void.class,new Class[]{android.os.Bundle.class}); checks++;
        System.out.println("NATIVE_ROUTE_PROBE_PASS assertions="+checks);
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Native entry selection failed"); }
}
