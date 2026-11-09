package com.dracolich777.clover.catalog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.NodeNames;
import com.dracolich777.clover.util.Text;

public final class CatalogEntry {
    public String node = "";

    public String displayName = "";
    public String description = "";

    public List<String> durations = new ArrayList<>();

    public String maxDuration = "";
    public List<String> allowedGroups = new ArrayList<>();

    transient Duration max;
    transient String title = "";
    transient String titleLower = "";
    transient String haystack = "";

    public Duration max() { return max; }
    public String title() { return title; }

    public Duration allowedDuration(String requested) {
        Duration d = Durations.parse(requested);
        return (d == null || d.compareTo(max) > 0) ? null : d;
    }

    void prepare() {
        String auto = NodeNames.friendly(node);
        title = (displayName != null && !displayName.isBlank()) ? displayName : auto;
        titleLower = Text.strip(title).toLowerCase(Locale.ROOT);
        String n = node.toLowerCase(Locale.ROOT);
        haystack = titleLower + " " + auto.toLowerCase(Locale.ROOT) + " " + n.replace('.', ' ') + " " + n + " "
                + Text.strip(description == null ? "" : description).toLowerCase(Locale.ROOT);
    }

    int score(String token) {
        if (titleLower.startsWith(token) || titleLower.contains(" " + token)) return 3;
        if (titleLower.contains(token)) return 2;
        return haystack.contains(token) ? 1 : 0;
    }
}