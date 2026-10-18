package cn.lzy.gt7p.installerclean;

final class RoutingPolicy {
    static int result(boolean installing, boolean frontendCompatible, int original) {
        if (!installing || !frontendCompatible) return original;
        return original == 1 || original == 11 ? 0 : original;
    }
}
