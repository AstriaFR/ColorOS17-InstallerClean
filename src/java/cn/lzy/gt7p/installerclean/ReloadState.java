package cn.lzy.gt7p.installerclean;

/** Transfer only boot/host objects; never carry a retired module's classes into its successor. */
final class ReloadState {
    record Target(String packageName, ClassLoader loader, long version) { }

    static Object[] save(Target target) {
        return new Object[]{target.packageName(), target.loader(), Long.valueOf(target.version())};
    }

    static Target restore(Object state) {
        if (!(state instanceof Object[] values) || values.length != 3
                || !(values[0] instanceof String name) || name.isEmpty()
                || !(values[1] instanceof ClassLoader loader)
                || !(values[2] instanceof Long version) || version < 0) {
            throw new IllegalArgumentException("Missing or invalid installer reload state");
        }
        return new Target(name, loader, version);
    }
}
