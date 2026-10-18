package cn.lzy.gt7p.installerclean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One canonical set supplies both the count and every row in the native details page. */
final class PermissionCatalog {
    interface Resolver { Metadata resolve(String permission); }
    enum Category { SENSITIVE, NORMAL, CUSTOM }
    record Metadata(String label, String description, boolean system, boolean dangerous) { }
    record Entry(String name, String label, String description, Category category) { }
    record Snapshot(List<Entry> entries, String[] names, String[] sensitive, String[] normal, String[] custom) { }

    static String[] normalize(String[] names) {
        Set<String> unique = new LinkedHashSet<>();
        if (names != null) for (String name : names) if (name != null && !name.trim().isEmpty()) unique.add(name);
        return unique.toArray(new String[0]);
    }

    static Snapshot build(String[] names, String[] sensitive, Resolver resolver, String fallback) {
        String[] all = normalize(names);
        Set<String> classified = new LinkedHashSet<>(Arrays.asList(normalize(sensitive)));
        List<Entry> entries = new ArrayList<>();
        List<String> privateNames = new ArrayList<>(), normalNames = new ArrayList<>(), customNames = new ArrayList<>();
        for (String name : all) {
            Metadata metadata = resolver.resolve(name);
            boolean hasLabel = metadata != null && metadata.label() != null && !metadata.label().trim().isEmpty();
            boolean hasDescription = metadata != null && metadata.description() != null && !metadata.description().trim().isEmpty();
            Category category = classified.contains(name) || metadata != null && metadata.dangerous()
                    ? Category.SENSITIVE : metadata != null && metadata.system() ? Category.NORMAL : Category.CUSTOM;
            entries.add(new Entry(name, hasLabel ? metadata.label() : name,
                    hasDescription ? metadata.description() : fallback, category));
            switch (category) {
                case SENSITIVE -> privateNames.add(name);
                case NORMAL -> normalNames.add(name);
                case CUSTOM -> customNames.add(name);
            }
        }
        // Stable sort: preserve APK declaration order within each category.
        entries.sort(Comparator.comparing(Entry::category));
        return new Snapshot(Collections.unmodifiableList(entries), all,
                privateNames.toArray(new String[0]), normalNames.toArray(new String[0]), customNames.toArray(new String[0]));
    }
}
