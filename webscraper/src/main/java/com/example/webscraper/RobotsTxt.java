package com.example.webscraper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A minimal robots.txt matcher: honours the {@code Allow} and {@code Disallow} rules for
 * the group that best matches our user agent (falling back to {@code *}). The longest
 * matching rule wins, and {@code Allow} wins a tie. Wildcards ({@code *}) and end anchors
 * ({@code $}) are supported.
 */
public final class RobotsTxt {

    private record Rule(String pattern, boolean allow) { }

    private static final RobotsTxt ALLOW_ALL = new RobotsTxt(List.of());

    private final List<Rule> rules;

    private RobotsTxt(List<Rule> rules) {
        this.rules = rules;
    }

    public static RobotsTxt allowAll() {
        return ALLOW_ALL;
    }

    /**
     * @param body      the robots.txt file contents
     * @param userAgent our full User-Agent string; its first token (e.g. "WebScraper" from
     *                  "WebScraper/1.0 (+https://...)") is matched against group names
     */
    public static RobotsTxt parse(String body, String userAgent) {
        String token = userAgent.split("[/\\s]", 2)[0].toLowerCase(Locale.ROOT);
        Groups groups = new Groups(token);

        boolean lastWasAgent = false;
        for (String raw : body.split("\\R")) {
            String line = raw.replaceFirst("#.*", "").trim();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            if (field.equals("user-agent")) {
                if (!lastWasAgent) {
                    groups.startGroup();
                }
                groups.agents.add(value.toLowerCase(Locale.ROOT));
                lastWasAgent = true;
            } else {
                lastWasAgent = false;
                // An empty Disallow means "allow everything" and adds no rule.
                if ((field.equals("allow") || field.equals("disallow")) && !value.isEmpty()) {
                    groups.rules.add(new Rule(value, field.equals("allow")));
                }
            }
        }
        groups.startGroup();
        return new RobotsTxt(groups.foundSpecific ? groups.specific : groups.wildcard);
    }

    /** Accumulates rules group by group, keeping the ones that apply to us. */
    private static final class Groups {
        final String token;
        final List<Rule> specific = new ArrayList<>();
        final List<Rule> wildcard = new ArrayList<>();
        boolean foundSpecific;
        List<String> agents = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();

        Groups(String token) {
            this.token = token;
        }

        /** Files the current group (if any) and begins a new one. */
        void startGroup() {
            for (String agent : agents) {
                if (agent.equals("*")) {
                    wildcard.addAll(rules);
                } else if (agent.equals(token)) {
                    specific.addAll(rules);
                    foundSpecific = true;
                }
            }
            agents = new ArrayList<>();
            rules = new ArrayList<>();
        }
    }

    /** @param pathAndQuery the URL path plus optional "?query", e.g. "/a/b?c=d" */
    public boolean isAllowed(String pathAndQuery) {
        Rule best = null;
        for (Rule rule : rules) {
            if (matches(rule.pattern(), pathAndQuery)) {
                if (best == null
                        || rule.pattern().length() > best.pattern().length()
                        || (rule.pattern().length() == best.pattern().length() && rule.allow())) {
                    best = rule;
                }
            }
        }
        return best == null || best.allow();
    }

    private static boolean matches(String pattern, String path) {
        boolean anchored = pattern.endsWith("$");
        String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        StringBuilder regex = new StringBuilder();
        for (String part : p.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        if (!anchored) {
            regex.append(".*");
        }
        return path.matches(regex.toString());
    }
}
