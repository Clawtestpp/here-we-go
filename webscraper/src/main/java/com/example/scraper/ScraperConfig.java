package com.example.scraper;

/**
 * Settings shared by the scraper and the crawler.
 *
 * @param userAgent     User-Agent header sent with every request and matched against robots.txt
 * @param timeoutMillis connect + read timeout per request
 * @param maxBodyBytes  maximum response body size to download (0 = unlimited)
 * @param selector      optional CSS selector whose matches are collected into {@link PageResult#selected()}
 * @param attribute     if set, collect this attribute of each selector match instead of its text
 * @param maxDepth      how many links away from the start URL to follow (0 = only the start URL)
 * @param maxPages      hard cap on the number of pages fetched
 * @param delayMillis   minimum pause between requests (raised to robots.txt Crawl-delay if larger)
 * @param sameHostOnly  only follow links on the start URL's host
 * @param respectRobots honour robots.txt Allow/Disallow rules
 */
public record ScraperConfig(
        String userAgent,
        int timeoutMillis,
        int maxBodyBytes,
        String selector,
        String attribute,
        int maxDepth,
        int maxPages,
        long delayMillis,
        boolean sameHostOnly,
        boolean respectRobots) {

    public static final String DEFAULT_USER_AGENT =
            "JavaWebScraper/1.0 (+https://github.com/clawtestpp/here-we-go)";

    public static ScraperConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String userAgent = DEFAULT_USER_AGENT;
        private int timeoutMillis = 10_000;
        private int maxBodyBytes = 5 * 1024 * 1024;
        private String selector;
        private String attribute;
        private int maxDepth = 0;
        private int maxPages = 50;
        private long delayMillis = 500;
        private boolean sameHostOnly = true;
        private boolean respectRobots = true;

        public Builder userAgent(String v) { this.userAgent = v; return this; }
        public Builder timeoutMillis(int v) { this.timeoutMillis = v; return this; }
        public Builder maxBodyBytes(int v) { this.maxBodyBytes = v; return this; }
        public Builder selector(String v) { this.selector = v; return this; }
        public Builder attribute(String v) { this.attribute = v; return this; }
        public Builder maxDepth(int v) { this.maxDepth = v; return this; }
        public Builder maxPages(int v) { this.maxPages = v; return this; }
        public Builder delayMillis(long v) { this.delayMillis = v; return this; }
        public Builder sameHostOnly(boolean v) { this.sameHostOnly = v; return this; }
        public Builder respectRobots(boolean v) { this.respectRobots = v; return this; }

        public ScraperConfig build() {
            if (maxDepth < 0) throw new IllegalArgumentException("maxDepth must be >= 0");
            if (maxPages < 1) throw new IllegalArgumentException("maxPages must be >= 1");
            if (delayMillis < 0) throw new IllegalArgumentException("delayMillis must be >= 0");
            if (timeoutMillis < 1) throw new IllegalArgumentException("timeoutMillis must be >= 1");
            if (maxBodyBytes < 0) throw new IllegalArgumentException("maxBodyBytes must be >= 0");
            return new ScraperConfig(userAgent, timeoutMillis, maxBodyBytes, selector, attribute,
                    maxDepth, maxPages, delayMillis, sameHostOnly, respectRobots);
        }
    }
}
