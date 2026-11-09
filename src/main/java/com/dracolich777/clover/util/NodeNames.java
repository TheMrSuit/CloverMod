package com.dracolich777.clover.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Turns LuckPerms nodes into readable names. Pure Java, no Minecraft imports. */
public final class NodeNames {
    private static final Pattern CAMEL = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Set<String> VANILLA_NS = Set.of("minecraft", "neoforge", "forge", "vanilla");

    private static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("tp", "Teleport"), Map.entry("tpa", "Teleport Request"), Map.entry("tphere", "Teleport Here"),
            Map.entry("xp", "Experience"), Map.entry("gm", "Gamemode"), Map.entry("msg", "Message"),
            Map.entry("w", "Whisper"), Map.entry("pvp", "PvP"), Map.entry("tnt", "TNT"), Map.entry("id", "ID"),
            Map.entry("uuid", "UUID"), Map.entry("gui", "GUI"), Map.entry("url", "URL"), Map.entry("ip", "IP"),
            Map.entry("op", "Op"), Map.entry("setblock", "Set Block"), Map.entry("spawnpoint", "Spawn Point"),
            Map.entry("setworldspawn", "Set World Spawn"), Map.entry("worldborder", "World Border"),
            Map.entry("playsound", "Play Sound"), Map.entry("banip", "Ban IP"), Map.entry("pardonip", "Pardon IP"));

    private static final Map<String, String> NAMESPACES = Map.ofEntries(
            Map.entry("worldedit", "WorldEdit"), Map.entry("worldguard", "WorldGuard"),
            Map.entry("luckperms", "LuckPerms"), Map.entry("essentials", "Essentials"),
            Map.entry("essentialsx", "EssentialsX"), Map.entry("minecraft", "Minecraft"),
            Map.entry("clover", "Clover"), Map.entry("neoforge", "NeoForge"), Map.entry("forge", "Forge"),
            Map.entry("coreprotect", "CoreProtect"), Map.entry("ftbchunks", "FTB Chunks"),
            Map.entry("ftbranks", "FTB Ranks"), Map.entry("ftbessentials", "FTB Essentials"));

    private static volatile Map<String, String> overrides = Map.of();
    private static final Map<String, String> memo = new ConcurrentHashMap<>();

    private NodeNames() {}

    /** Keys are lowercase nodes; a trailing '*' applies to everything under that prefix. */
    public static void setOverrides(Map<String, String> o) {
        overrides = Map.copyOf(o);
        memo.clear();
    }

    public static String friendly(String node) {
        return memo.computeIfAbsent(node, NodeNames::compute);
    }

    private static String compute(String node) {
        String over = override(node.toLowerCase(Locale.ROOT));
        if (over != null) return over;

        List<String> parts = new ArrayList<>();
        for (String s : node.split("\\.")) if (!s.isEmpty()) parts.add(s);
        boolean wildcard = false;
        if (!parts.isEmpty() && parts.get(parts.size() - 1).equals("*")) {
            wildcard = true;
            parts.remove(parts.size() - 1);
        }
        if (parts.isEmpty()) return wildcard ? "Everything (all)" : node;

        int ci = -1;
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i).toLowerCase(Locale.ROOT);
            if (p.equals("command") || p.equals("commands")) { ci = i; break; }
        }
        boolean command = ci >= 0;
        String namespace;
        List<String> rest;
        if (command) {
            List<String> ns = new ArrayList<>(parts.subList(0, ci));
            if (!ns.isEmpty() && VANILLA_NS.contains(ns.get(0).toLowerCase(Locale.ROOT))) ns.remove(0);
            namespace = ns.isEmpty() ? "" : nsName(ns);
            rest = parts.subList(ci + 1, parts.size());
        } else if (parts.size() == 1 && wildcard) {
            namespace = nsName(parts);
            rest = List.of();
        } else if (parts.size() > 1) {
            namespace = nsName(List.of(parts.get(0)));
            rest = parts.subList(1, parts.size());
        } else {
            namespace = "";
            rest = parts;
        }

        List<String> rw = new ArrayList<>();
        for (String s : rest) rw.add(words(s));

        String title;
        if (command) {
            if (namespace.isEmpty()) {
                if (rw.isEmpty()) {
                    title = "Commands";
                } else {
                    title = rw.get(0) + " Command";
                    if (rw.size() > 1) title += ": " + String.join(" ", rw.subList(1, rw.size()));
                }
                if (wildcard && rw.size() != 1) title += " (all)"; // "Gamemode Command" already means the whole command
            } else {
                title = rw.isEmpty() ? namespace + " Commands" : namespace + " Command: " + String.join(" ", rw);
                if (wildcard) title += " (all)";
            }
        } else {
            if (namespace.isEmpty()) title = rw.isEmpty() ? node : String.join(" ", rw);
            else title = rw.isEmpty() ? namespace : namespace + ": " + String.join(" ", rw);
            if (wildcard) title += " (all)";
        }
        return title;
    }

    private static String override(String lower) {
        Map<String, String> o = overrides;
        if (o.isEmpty()) return null;
        String exact = o.get(lower);
        if (exact != null) return exact;
        String best = null;
        int bestLen = -1;
        for (Map.Entry<String, String> en : o.entrySet()) {
            String k = en.getKey();
            if (!k.endsWith("*")) continue;
            String prefix = k.substring(0, k.length() - 1);
            if (lower.startsWith(prefix) && prefix.length() > bestLen) {
                best = en.getValue();
                bestLen = prefix.length();
            }
        }
        return best;
    }

    private static String nsName(List<String> segs) {
        if (segs.size() == 1) {
            String n = NAMESPACES.get(segs.get(0).toLowerCase(Locale.ROOT));
            if (n != null) return n;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : segs) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(words(s));
        }
        return sb.toString();
    }

    private static String words(String seg) {
        String whole = WORDS.get(seg.toLowerCase(Locale.ROOT));
        if (whole != null) return whole;
        String spaced = CAMEL.matcher(seg).replaceAll("$1 $2")
                .replace('-', ' ').replace('_', ' ').replace(':', ' ').trim();
        StringBuilder sb = new StringBuilder();
        for (String w : spaced.split("\\s+")) {
            if (w.isEmpty()) continue;
            String mapped = WORDS.get(w.toLowerCase(Locale.ROOT));
            if (sb.length() > 0) sb.append(' ');
            sb.append(mapped != null ? mapped : cap(w));
        }
        return sb.length() == 0 ? seg : sb.toString();
    }

    private static String cap(String w) {
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }
}