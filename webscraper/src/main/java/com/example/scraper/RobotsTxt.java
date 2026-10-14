package com.example.scraper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal robots.txt parser (RFC 9309): picks the most specific matching user-agent group,
 * supports Allow/Disallow with {@code *} wildcards and the {@code $} end anchor, using
 * longest-match-wins with Allow winning ties. Also reads the non-standard Crawl-delay.
 */
public final class RobotsTxt {

    private record Rule(String pattern, boolean allow) {}

    private static final RobotsTxt ALLOW_ALL = new RobotsTxt(List.of(), 0);

    private final List<Rule> rules;
    private final long crawlDelayMillis;

    private RobotsTxt(List<Rule> rules, long crawlDelayMillis) {
        this.rules = rules;
        this.crawlDelayMillis = crawlDelayMillis;
    }

    public static RobotsTxt allowAll() {
        return ALLOW_ALL;
    }

    /** Parses robots.txt content and keeps the rules that apply to {@code userAgent}. */
    public static RobotsTxt parse(String content, String userAgent) {
        if (content == null || content.isBlank()) return ALLOW_ALL;
        // Product token: "JavaWebScraper/1.0 (...)" -> "javawebscraper"
        String token = userAgent.split("[/\\s]", 2)[0].toLowerCase(Locale.ROOT);

        List<Rule> specific = null, wildcard = null;
        long specificDelay = 0, wildcardDelay = 0;

        List<String> groupAgents = new ArrayList<>();
        List<Rule> groupRules = new ArrayList<>();
        long groupDelay = 0;
        boolean inRules = false;

        List<String> lines = new ArrayList<>(content.lines().toList());
        lines.add("user-agent: \u0000end"); // sentinel flushes the final group
        for (String raw : lines) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            switch (key) {
                case "user-agent" -> {
                    if (inRules) {
                        // A new group starts; flush the previous one.
                        for (String agent : groupAgents) {
                            if (agent.equals("*")) {
                                if (wildcard == null) wildcard = new ArrayList<>();
                                wildcard.addAll(groupRules);
                                wildcardDelay = Math.max(wildcardDelay, groupDelay);
                            } else if (agent.split("[/\\s]", 2)[0].equals(token)) {
                                if (specific == null) specific = new ArrayList<>();
                                specific.addAll(groupRules);
                                specificDelay = Math.max(specificDelay, groupDelay);
                            }
                        }
                        groupAgents = new ArrayList<>();
                        groupRules = new ArrayList<>();
                        groupDelay = 0;
                        inRules = false;
                    }
                    groupAgents.add(value.toLowerCase(Locale.ROOT));
                }
                case "allow", "disallow" -> {
                    inRules = true;
                    // An empty Disallow means "allow everything" and adds no rule.
                    if (!value.isEmpty()) groupRules.add(new Rule(value, key.equals("allow")));
                }
                case "crawl-delay" -> {
                    inRules = true;
                    try {
                        groupDelay = (long) (Double.parseDouble(value) * 1000);
                    } catch (NumberFormatException ignored) {
                        // Malformed delay: ignore.
                    }
                }
                default -> { /* sitemap and unknown keys don't affect access */ }
            }
        }

        if (specific != null) return new RobotsTxt(List.copyOf(specific), specificDelay);
        if (wildcard != null) return new RobotsTxt(List.copyOf(wildcard), wildcardDelay);
        return ALLOW_ALL;
    }

    /** Whether the given URL path (including any query string) may be fetched. */
    public boolean isAllowed(String pathAndQuery) {
        String path = pathAndQuery == null || pathAndQuery.isEmpty() ? "/" : pathAndQuery;
        Rule best = null;
        for (Rule rule : rules) {
            if (matches(rule.pattern(), path)) {
                if (best == null
                        || rule.pattern().length() > best.pattern().length()
                        || (rule.pattern().length() == best.pattern().length() && rule.allow())) {
                    best = rule;
                }
            }
        }
        return best == null || best.allow();
    }

    public long crawlDelayMillis() {
        return crawlDelayMillis;
    }

    /** Glob-style match where {@code *} matches any sequence and a trailing {@code $} anchors the end. */
    static boolean matches(String pattern, String path) {
        boolean anchored = pattern.endsWith("$");
        String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        return matchFrom(p, 0, path, 0, anchored);
    }

    private static boolean matchFrom(String p, int pi, String s, int si, boolean anchored) {
        while (pi < p.length()) {
            char c = p.charAt(pi);
            if (c == '*') {
                while (pi < p.length() && p.charAt(pi) == '*') pi++; // collapse runs of *
                if (pi == p.length()) return true;
                for (int k = si; k <= s.length(); k++) {
                    if (matchFrom(p, pi, s, k, anchored)) return true;
                }
                return false;
            }
            if (si >= s.length() || s.charAt(si) != c) return false;
            pi++;
            si++;
        }
        return !anchored || si == s.length();
    }
}
