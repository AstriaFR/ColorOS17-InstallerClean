package cn.lzy.gt7p.installerclean;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Read-only structural matching. Never invokes constructors, methods or static initializers. */
final class StructuralProbe {
    record Prepared(Profile.Rule rule, Profile.Group group, List<Method> targets,
                    List<Executable> callers, Set<String> fingerprint) { }
    record Decision(Prepared selected, boolean ambiguous, List<String> reasons) { }

    static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        if (name.endsWith("[]")) return Array.newInstance(type(loader, name.substring(0, name.length() - 2)), 0).getClass();
        return switch (name) {
            case "void" -> void.class;
            case "boolean" -> boolean.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "char" -> char.class;
            case "float" -> float.class;
            case "double" -> double.class;
            default -> Class.forName(name, false, loader);
        };
    }

    private static Executable executable(ClassLoader loader, String[] spec) throws ReflectiveOperationException {
        Class<?> owner = type(loader, spec[0]);
        Class<?>[] parameters = new Class<?>[spec.length - 3];
        for (int i = 3; i < spec.length; i++) parameters[i - 3] = type(loader, spec[i]);
        Executable result;
        if (spec[1].equals("<init>")) {
            if (!spec[2].equals("void")) throw new NoSuchMethodException("Constructor return type");
            result = owner.getDeclaredConstructor(parameters);
        } else {
            Method method = owner.getDeclaredMethod(spec[1], parameters);
            if (method.getReturnType() != type(loader, spec[2])) throw new NoSuchMethodException(spec[0] + "#" + spec[1] + " return type");
            result = method;
        }
        result.setAccessible(true);
        return result;
    }

    static Prepared prepare(ClassLoader loader, Profile.Rule rule, Profile.Group group) throws ReflectiveOperationException {
        if (group.hooks.length == 0) throw new NoSuchMethodException("Empty feature group");
        if (group.hookStatic.length != group.hooks.length || group.dependencyStatic.length != group.dependencies.length)
            throw new IllegalArgumentException("Invalid structural template");
        List<Method> targets = new ArrayList<>();
        List<Executable> callers = new ArrayList<>();
        Set<String> fingerprint = new LinkedHashSet<>();
        for (String[] pair : group.assignable) {
            if (!type(loader, pair[0]).isAssignableFrom(type(loader, pair[1])))
                throw new NoSuchMethodException("Binding type mismatch: " + pair[1] + " is not " + pair[0]);
            fingerprint.add("B:" + Arrays.toString(pair));
        }
        for (int i = 0; i < group.hooks.length; i++) {
            String[] spec = group.hooks[i];
            Executable target = executable(loader, Arrays.copyOfRange(spec, 1, spec.length));
            if (!(target instanceof Method method) || Modifier.isStatic(target.getModifiers()) != group.hookStatic[i])
                throw new NoSuchMethodException("Hook static/instance mismatch: " + spec[1] + "#" + spec[2]);
            targets.add(method);
            fingerprint.add("H:" + group.hookStatic[i] + ":" + Arrays.toString(spec));
        }
        for (int i = 0; i < group.dependencies.length; i++) {
            String[] spec = group.dependencies[i];
            Executable dependency = executable(loader, spec);
            if (Modifier.isStatic(dependency.getModifiers()) != group.dependencyStatic[i])
                throw new NoSuchMethodException("Dependency static/instance mismatch: " + spec[0] + "#" + spec[1]);
            fingerprint.add("D:" + group.dependencyStatic[i] + ":" + Arrays.toString(spec));
        }
        for (String[] spec : group.fields) {
            Field field = type(loader, spec[0]).getDeclaredField(spec[1]);
            if (field.getType() != type(loader, spec[2]) || Modifier.isStatic(field.getModifiers()) != Boolean.parseBoolean(spec[3]))
                throw new NoSuchFieldException(spec[0] + "#" + spec[1] + " type/static mismatch");
            field.setAccessible(true);
            fingerprint.add("F:" + Arrays.toString(spec));
        }
        for (String[] spec : group.deopt) {
            callers.add(executable(loader, spec));
            fingerprint.add("C:" + Arrays.toString(spec));
        }
        return new Prepared(rule, group, List.copyOf(targets), List.copyOf(callers), Collections.unmodifiableSet(fingerprint));
    }

    static Decision select(ClassLoader loader, Feature feature, Profile.Rule family) {
        return select(loader, feature, family, Arrays.asList(Profile.ALL));
    }

    static Decision select(ClassLoader loader, Feature feature, Profile.Rule family, List<Profile.Rule> templates) {
        List<Prepared> matches = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (Profile.Rule rule : templates) {
            if (family != null && rule != family) continue;
            Profile.Group group = rule.group(feature);
            if (group == null) continue;
            try { matches.add(prepare(loader, rule, group)); }
            catch (ReflectiveOperationException | LinkageError | SecurityException failure) {
                reasons.add(rule.id + ":" + failure.getClass().getSimpleName() + ":" + failure.getMessage());
            }
        }
        return choose(matches, reasons);
    }

    static Decision choose(List<Prepared> matches, List<String> reasons) {
        // Equivalent templates are one choice. A strict compatible superset includes the same
        // hook actions/dependencies plus optional additions. Incomparable matches are ambiguous.
        List<Prepared> maximal = new ArrayList<>();
        for (Prepared candidate : matches) {
            boolean subset = false;
            for (Prepared other : matches) {
                if (other.fingerprint().size() > candidate.fingerprint().size()
                        && other.fingerprint().containsAll(candidate.fingerprint())) { subset = true; break; }
            }
            if (!subset && maximal.stream().noneMatch(item -> item.fingerprint().equals(candidate.fingerprint()))) maximal.add(candidate);
        }
        return new Decision(maximal.size() == 1 ? maximal.get(0) : null, maximal.size() > 1, List.copyOf(reasons));
    }
}
