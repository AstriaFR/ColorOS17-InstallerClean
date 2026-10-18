package cn.lzy.gt7p.installerclean;

import com.oplus.appdetail.modelv2.parsing.b;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Local JVM regressions; no Android device, app install or compatibility activation. */
public final class RegressionTest {
    private static int assertions;
    private static int probeInitializations;
    public static final class ProbeTarget {
        static { probeInitializations++; }
        public static String TOKEN = "metadata";
        public ProbeTarget() { }
        public int run(String input) { throw new AssertionError("Probe invoked target"); }
        public static String helper() { throw new AssertionError("Probe invoked helper"); }
        public static String extra() { throw new AssertionError("Probe invoked extra"); }
    }
    private static Profile.Rule template(String label, Profile.Group... groups) {
        return new Profile.Rule(label, false, "unknown-version", "", "", "", "", "", "", groups);
    }
    private static void structuralTests() throws Exception {
        String owner = ProbeTarget.class.getName();
        String[][] hooks = {{"NOOP", owner, "run", "int", "java.lang.String"}};
        String[][] deps = {{owner, "helper", "java.lang.String"}, {owner, "<init>", "void"}};
        String[][] fields = {{owner, "TOKEN", "java.lang.String", "true"}};
        String[][] callers = {{owner, "<init>", "void"}};
        var base = new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, deps, new boolean[]{true, false}, fields, callers);
        var rule = template("unknown-installer-build", base);
        ClassLoader loader = RegressionTest.class.getClassLoader();
        var prepared = StructuralProbe.prepare(loader, rule, base);
        check(prepared.targets().size() == 1 && prepared.callers().size() == 1, "Resolve exact hooks and deopt constructors");
        check(probeInitializations == 0, "Structural probes must not initialize or execute target classes");
        check(StructuralProbe.select(loader, Feature.RECOMMENDATIONS, null, List.of(rule)).selected() != null,
                "Unknown version labels must not prevent structural selection");
        var missing = new Profile.Group(Feature.FLOW, new String[][]{{"NOOP", "missing.OemClass", "run", "void"}}, new boolean[]{false},
                new String[0][], new boolean[0], new String[0][], new String[0][]);
        var partlyKnown = template("partial-template", missing, base);
        check(StructuralProbe.select(loader, Feature.FLOW, null, List.of(partlyKnown)).selected() == null, "Missing core must fail closed");
        check(StructuralProbe.select(loader, Feature.RECOMMENDATIONS, null, List.of(partlyKnown)).selected() != null,
                "A missing core must not disable independently matched recommendations");
        var invalid = List.of(
                new Profile.Group(Feature.RECOMMENDATIONS, new String[][]{{"NOOP", owner, "run", "boolean", "java.lang.String"}}, new boolean[]{false}, deps, new boolean[]{true,false}, fields, callers),
                new Profile.Group(Feature.RECOMMENDATIONS, new String[][]{{"NOOP", owner, "run", "int", "int"}}, new boolean[]{false}, deps, new boolean[]{true,false}, fields, callers),
                new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{true}, deps, new boolean[]{true,false}, fields, callers),
                new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, deps, new boolean[]{false,false}, fields, callers),
                new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, deps, new boolean[]{true,false}, new String[][]{{owner,"TOKEN","int","true"}}, callers),
                new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, deps, new boolean[]{true,false}, new String[][]{{owner,"TOKEN","java.lang.String","false"}}, callers),
                new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, deps, new boolean[]{true,false}, new String[][]{{owner,"MISSING","java.lang.String","true"}}, callers),
                new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, new String[][]{{owner,"<init>","void","int"}}, new boolean[]{false}, fields, callers),
                new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, deps, new boolean[]{true,false}, fields, new String[][]{{owner,"run","int","int"}}));
        for (Profile.Group group : invalid) {
            var result = StructuralProbe.select(loader, Feature.RECOMMENDATIONS, null, List.of(template("invalid", group)));
            check(result.selected() == null && !result.reasons().isEmpty(), "Mismatched signatures/types/staticness must be reported and skipped");
        }
        var superset = new Profile.Group(Feature.RECOMMENDATIONS,
                new String[][]{hooks[0], {"NULL", owner, "extra", "java.lang.String"}}, new boolean[]{false,true}, deps, new boolean[]{true,false}, fields, callers);
        var larger = StructuralProbe.prepare(loader, template("larger", superset), superset);
        check(StructuralProbe.choose(List.of(prepared, larger), List.of()).selected() == larger,
                "Select a strict compatible superset instead of duplicating shared hooks");
        var equivalent = StructuralProbe.prepare(loader, template("equivalent", base), base);
        check(!StructuralProbe.choose(List.of(prepared, equivalent), List.of()).ambiguous(), "Equivalent templates are one match");
        var conflict = new Profile.Group(Feature.RECOMMENDATIONS, new String[][]{{"OTHER_ACTION", owner,"run","int","java.lang.String"}},
                new boolean[]{false}, deps, new boolean[]{true,false}, fields, callers);
        var conflicting = StructuralProbe.prepare(loader, template("conflicting", conflict), conflict);
        var ambiguous = StructuralProbe.choose(List.of(prepared, conflicting), List.of());
        check(ambiguous.ambiguous() && ambiguous.selected() == null, "Conflicting matches must not be chosen by template order");
        check(StructuralProbe.select(loader, Feature.RECOMMENDATIONS, partlyKnown, List.of(rule)).selected() == null,
                "Dependent feature must stay in the matched core family");
        check(probeInitializations == 0, "All successful and rejected probes must remain free of target execution");
        var badContract = new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, deps,
                new boolean[]{true,false}, fields, callers, new String[][]{{"java.lang.String",owner}});
        check(StructuralProbe.select(loader,Feature.RECOMMENDATIONS,null,List.of(template("bad-contract",badContract))).selected() == null,
                "Reject individually valid declarations with incompatible binding types");
        var goodContract = new Profile.Group(Feature.RECOMMENDATIONS, hooks, new boolean[]{false}, deps,
                new boolean[]{true,false}, fields, callers, new String[][]{{"java.lang.Object",owner}});
        check(StructuralProbe.select(loader,Feature.RECOMMENDATIONS,null,List.of(template("good-contract",goodContract))).selected() != null,
                "Accept verified assignability without invoking constructors");
        for (int original : new int[]{0,1,2,11,12,-1}) {
            check(RoutingPolicy.result(false, true, original) == original, "Uninstall branches must remain unchanged");
            check(RoutingPolicy.result(true, false, original) == original, "Unmatched frontend must retain backend route result");
            check(RoutingPolicy.result(true, true, original) == (original == 1 || original == 11 ? 0 : original),
                    "Only known feature-switch fallback values may be remapped");
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
    public static void main(String[] args) throws Throwable {
        structuralTests();
        ClassLoader hostLoader = ClassLoader.getPlatformClassLoader();
        Object[] saved = ReloadState.save(new ReloadState.Target("com.oplus.appdetail", hostLoader, 51762L));
        check(saved.getClass() == Object[].class && saved[0] instanceof String && saved[2] instanceof Long,
                "Reload state must contain only boot/host objects, never a retired module record");
        var restored = ReloadState.restore(saved);
        check(restored.loader() == hostLoader && restored.version() == 51762L && restored.packageName().equals("com.oplus.appdetail"),
                "Reload must retain the actual host classloader and installer version");
        for (Object invalid : new Object[]{null, new Object[0], new Object[]{"", hostLoader, 51762L},
                new Object[]{"com.oplus.appdetail", null, 51762L}, new Object[]{"com.oplus.appdetail", hostLoader, -1L},
                new Object[]{"com.oplus.appdetail", hostLoader, "51762"}}) {
            try { ReloadState.restore(invalid); throw new AssertionError("Invalid reload state was accepted"); }
            catch (IllegalArgumentException expected) { check(true, "Malformed handover must fail before hook replacement"); }
        }
        check(ReloadState.restore(new Object[]{"com.oplus.appdetail", hostLoader, 0L}).version() == 0L,
                "Missing diagnostic version must not block structural matching or hot reload");
        PermissionCatalog.Resolver resolver = name -> switch (name) {
            case "camera" -> new PermissionCatalog.Metadata("Camera", "Use the camera", true, true);
            case "internet" -> new PermissionCatalog.Metadata("Network", "Access the network", true, false);
            case "missing-description" -> new PermissionCatalog.Metadata("Existing label", null, true, false);
            case "registered-custom" -> new PermissionCatalog.Metadata("App feature", "A custom permission", false, false);
            case "custom-dangerous" -> new PermissionCatalog.Metadata("Sensitive app feature", null, false, true);
            case "missing-sensitive" -> new PermissionCatalog.Metadata(null, null, true, true);
            default -> null;
        };
        var mixed = PermissionCatalog.build(new String[]{"camera", "custom", "internet", "missing-description"},
                new String[]{"camera", "not-requested"}, resolver, "Unavailable");
        check(mixed.entries().size() == 4 && mixed.names().length == 4, "All requested permissions must remain visible");
        check(mixed.entries().get(3).label().equals("custom"), "Unregistered permission must show its raw name");
        check(mixed.entries().get(3).description().equals("Unavailable"), "Unregistered permission needs a neutral description");
        check(mixed.entries().get(2).label().equals("Existing label"), "Keep an available label even without a description");
        check(mixed.entries().get(2).description().equals("Unavailable"), "Missing descriptions need a fallback, not a missing row");
        check(Arrays.equals(mixed.sensitive(), new String[]{"camera"}), "Do not count unrequested sensitive permissions");
        check(mixed.normal().length + mixed.sensitive().length + mixed.custom().length == mixed.names().length, "Classification must cover the canonical set");
        check(Arrays.equals(mixed.normal(), new String[]{"internet", "missing-description"}), "Missing metadata must not reclassify a system permission as custom");
        check(Arrays.equals(mixed.custom(), new String[]{"custom"}), "Unregistered permissions belong to the custom group");
        check(mixed.entries().stream().map(PermissionCatalog.Entry::name).toList().equals(
                Arrays.asList("camera", "internet", "missing-description", "custom")), "Sensitive, normal, custom order must be stable");
        var unknown = PermissionCatalog.build(new String[]{"custom-a", "custom-b"}, null, resolver, "Unavailable");
        check(unknown.entries().size() == 2 && unknown.custom().length == 2, "All-unknown permissions must have a custom group");
        check(unknown.sensitive().length == 0 && unknown.normal().length == 0, "Empty categories must have no rows");
        var known = PermissionCatalog.build(new String[]{"camera", "internet"}, new String[]{"camera"}, resolver, "Unavailable");
        check(known.custom().length == 0 && known.entries().get(0).category() == PermissionCatalog.Category.SENSITIVE,
                "Known permissions must retain native grouping");
        var empty = PermissionCatalog.build(null, new String[]{"camera"}, resolver, "Unavailable");
        check(empty.entries().isEmpty() && empty.sensitive().length == 0, "Empty APK must show zero permissions");
        var duplicates = PermissionCatalog.build(new String[]{"camera", null, "", "camera", "internet"}, null, resolver, "Unavailable");
        check(duplicates.names().length == 2 && duplicates.entries().size() == 2, "Counts and rows must normalize the same names");
        var categories = PermissionCatalog.build(new String[]{"registered-custom", "internet", "custom-dangerous", "camera", "missing-sensitive", "custom"},
                new String[]{"registered-custom"}, resolver, "Unavailable");
        check(Arrays.equals(categories.sensitive(), new String[]{"registered-custom", "custom-dangerous", "camera", "missing-sensitive"}),
                "Vendor-sensitive and dangerous permissions take precedence over custom classification");
        check(categories.entries().stream().map(PermissionCatalog.Entry::name).toList().equals(Arrays.asList(
                "registered-custom", "custom-dangerous", "camera", "missing-sensitive", "internet", "custom")),
                "Preserve declaration order within all three categories");
        check(categories.entries().get(3).label().equals("missing-sensitive"), "Sensitive permissions without metadata must remain visible");
        var registered = PermissionCatalog.build(new String[]{"registered-custom"}, null, resolver, "Unavailable");
        check(registered.custom().length == 1 && registered.normal().length == 0, "Complete third-party metadata still belongs to the custom group");
        for (Object original : new Object[]{new b.a(), new b.e(), new b.f(), new b.i()}) {
            AtomicInteger calls = new AtomicInteger(), replacements = new AtomicInteger();
            Object result = ParsingPolicy.choose(() -> { calls.incrementAndGet(); return original; }, () -> { replacements.incrementAndGet(); return new b.j(); });
            check(result == original, "Keep the native compatibility result instance: " + original.getClass());
            check(calls.get() == 1 && replacements.get() == 0, "Evaluate original once and do not construct a replacement for compatibility");
        }
        Object ordinary = new b.j();
        AtomicInteger calls = new AtomicInteger();
        check(ParsingPolicy.choose(() -> { calls.incrementAndGet(); return new b.j(); }, () -> ordinary) == ordinary,
                "Optional guide results must still use the simplified path");
        check(calls.get() == 1, "Optional guide dispatch must evaluate original once");
        for (Object optional : new Object[]{new b.c(),new b.d(),new b.h(),new b.k(),new b.l(),new b.m(),new b.n()}) {
            check(ParsingPolicy.choose(() -> optional, () -> ordinary) == ordinary, "Keep simplifying known optional prompts");
        }
        for (Object unknownState : new Object[]{new b.o(),new Object(),null}) {
            AtomicInteger replacements = new AtomicInteger();
            check(ParsingPolicy.choose(() -> unknownState, () -> { replacements.incrementAndGet(); return ordinary; }) == unknownState,
                    "Unknown and missing parsing states must remain original");
            check(replacements.get() == 0, "Never construct ordinary completion for an unrecognized parsing state");
        }
        IllegalStateException failure = new IllegalStateException("original failure");
        try {
            ParsingPolicy.choose(() -> { throw failure; }, () -> ordinary);
            throw new AssertionError("Original exceptions must not become successful parses");
        } catch (IllegalStateException expected) { check(expected == failure, "Keep the original failure"); }
        System.out.println("REGRESSION_PASS assertions=" + assertions);
    }
}
