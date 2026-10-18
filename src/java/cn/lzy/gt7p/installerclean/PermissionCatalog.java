package cn.lzy.gt7p.installerclean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One canonical set supplies both the count and every row in the native details page. */
final class PermissionCatalog {
    interface Resolver { Metadata resolve(String permission); }
    record Metadata(String label, String description) { }
    record Entry(String name, String label, String description, boolean sensitive) { }
    record Snapshot(List<Entry> entries, String[] names, String[] sensitive, String[] normal, boolean hasFallback) { }

    static String[] normalize(String[] names) {
        Set<String> unique = new LinkedHashSet<>();
        if (names != null) for (String name : names) if (name != null && !name.trim().isEmpty()) unique.add(name);
        return unique.toArray(new String[0]);
    }

    static Snapshot build(String[] names, String[] sensitive, Resolver resolver, String fallback) {
        String[] all = normalize(names);
        Set<String> classified = new LinkedHashSet<>(Arrays.asList(normalize(sensitive)));
        List<Entry> entries = new ArrayList<>();
        List<String> privateNames = new ArrayList<>(), otherNames = new ArrayList<>();
        boolean hasFallback = false;
        for (String name : all) {
            Metadata metadata = resolver.resolve(name);
            boolean complete = metadata != null && metadata.label() != null && !metadata.label().trim().isEmpty()
                    && metadata.description() != null && !metadata.description().trim().isEmpty();
            boolean privatePermission = classified.contains(name);
            entries.add(new Entry(name, complete ? metadata.label() : name,
                    complete ? metadata.description() : fallback, privatePermission));
            (privatePermission ? privateNames : otherNames).add(name);
            hasFallback |= !complete;
        }
        return new Snapshot(Collections.unmodifiableList(entries), all,
                privateNames.toArray(new String[0]), otherNames.toArray(new String[0]), hasFallback);
    }
}
