package com.dracolich777.clover.catalog;

import com.dracolich777.clover.util.Durations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class CatalogEntry {

  public String node = "";
  public String description = "";
  /** Suggested durations (tab-complete). */
  public List<String> durations = new ArrayList<>();
  /** Longest accepted duration; defaults to the longest suggested one. */
  public String maxDuration = "";
  public List<String> allowedGroups = new ArrayList<>();

  transient Duration max; // set by CatalogManager

  public Duration max() {
    return max;
  }

  /** Any valid duration up to the cap, or null. */
  public Duration allowedDuration(String requested) {
    Duration d = Durations.parse(requested);
    return (d == null || d.compareTo(max) > 0) ? null : d;
  }
}
