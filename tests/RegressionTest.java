package cn.lzy.gt7p.installerclean;

import com.oplus.appdetail.modelv2.parsing.b;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Local JVM regressions; no Android device, app install or compatibility activation. */
public final class RegressionTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
    public static void main(String[] args) throws Throwable {
        PermissionCatalog.Resolver resolver = name -> switch (name) {
            case "camera" -> new PermissionCatalog.Metadata("Camera", "Use the camera");
            case "internet" -> new PermissionCatalog.Metadata("Network", "Access the network");
            case "missing-description" -> new PermissionCatalog.Metadata("Existing label", null);
            default -> null;
        };
        var mixed = PermissionCatalog.build(new String[]{"camera", "custom", "internet", "missing-description"},
                new String[]{"camera", "not-requested"}, resolver, "Unavailable");
        check(mixed.entries().size() == 4 && mixed.names().length == 4, "All requested permissions must remain visible");
        check(mixed.entries().get(1).label().equals("custom"), "Unregistered permission must show its raw name");
        check(mixed.entries().get(1).description().equals("Unavailable"), "Unregistered permission needs a neutral description");
        check(mixed.entries().get(3).label().equals("missing-description"), "Missing descriptions must not discard the row");
        check(mixed.hasFallback(), "Unknown rows must use an unclassified complete list");
        check(Arrays.equals(mixed.sensitive(), new String[]{"camera"}), "Do not count unrequested sensitive permissions");
        check(mixed.normal().length + mixed.sensitive().length == mixed.names().length, "Classification must cover the canonical set");
        var unknown = PermissionCatalog.build(new String[]{"custom-a", "custom-b"}, null, resolver, "Unavailable");
        check(unknown.entries().size() == 2 && unknown.hasFallback(), "All-unknown permissions must not open an empty page");
        var known = PermissionCatalog.build(new String[]{"camera", "internet"}, new String[]{"camera"}, resolver, "Unavailable");
        check(!known.hasFallback() && known.entries().get(0).sensitive(), "Known permissions must retain normal native grouping");
        var empty = PermissionCatalog.build(null, new String[]{"camera"}, resolver, "Unavailable");
        check(empty.entries().isEmpty() && empty.sensitive().length == 0, "Empty APK must show zero permissions");
        var duplicates = PermissionCatalog.build(new String[]{"camera", null, "", "camera", "internet"}, null, resolver, "Unavailable");
        check(duplicates.names().length == 2 && duplicates.entries().size() == 2, "Counts and rows must normalize the same names");
        for (Object original : new Object[]{new b.a(), new b.e(), new b.f(), new b.i()}) {
            AtomicInteger calls = new AtomicInteger(), replacements = new AtomicInteger();
            Object result = ParsingPolicy.choose(() -> { calls.incrementAndGet(); return original; }, () -> { replacements.incrementAndGet(); return new b.j(); });
            check(result == original, "Keep the native compatibility result instance: " + original.getClass());
            check(calls.get() == 1 && replacements.get() == 0, "Evaluate original once and do not construct a replacement for compatibility");
        }
        Object ordinary = new b.j();
        AtomicInteger calls = new AtomicInteger();
        check(ParsingPolicy.choose(() -> { calls.incrementAndGet(); return new Object(); }, () -> ordinary) == ordinary,
                "Optional guide results must still use the simplified path");
        check(calls.get() == 1, "Optional guide dispatch must evaluate original once");
        IllegalStateException failure = new IllegalStateException("original failure");
        try {
            ParsingPolicy.choose(() -> { throw failure; }, () -> ordinary);
            throw new AssertionError("Original exceptions must not become successful parses");
        } catch (IllegalStateException expected) { check(expected == failure, "Keep the original failure"); }
        System.out.println("REGRESSION_PASS assertions=" + assertions);
    }
}
