package com.example.scraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Breadth-first crawler built on {@link WebScraper}. It is polite by default: it honours
 * robots.txt, waits between requests, stays on the start host and stops at a page limit.
 */
public class Crawler {

    private final ScraperConfig config;
    private final WebScraper scraper;
    private final Map<String, RobotsTxt> robotsCache = new HashMap<>();
    private long lastRequestAt = 0;

    public Crawler(ScraperConfig config) {
        this(config, new WebScraper(config));
    }

    Crawler(ScraperConfig config, WebScraper scraper) {
        this.config = config;
        this.scraper = scraper;
    }

    /** Crawls from {@code startUrl} and returns every page visited, in visit order. */
    public List<PageResult> crawl(String startUrl) {
        List<PageResult> results = new ArrayList<>();
        crawl(startUrl, results::add);
        return results;
    }

    /** Crawls from {@code startUrl}, handing each page to {@code onPage} as soon as it is scraped. */
    public void crawl(String startUrl, Consumer<PageResult> onPage) {
        String start = WebScraper.normalizeUrl(startUrl);
        if (start == null) {
            onPage.accept(PageResult.failure(startUrl, 0, -1, "Not a valid http(s) URL"));
            return;
        }
        String startHost = URI.create(start).getHost();

        record Pending(String url, int depth) {}
        Deque<Pending> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(new Pending(start, 0));
        seen.add(start);
        int fetched = 0;

        while (!queue.isEmpty() && fetched < config.maxPages()) {
            Pending next = queue.poll();
            URI uri = URI.create(next.url());

            RobotsTxt robots = config.respectRobots() ? robotsFor(uri) : RobotsTxt.allowAll();
            String pathAndQuery = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            if (!robots.isAllowed(pathAndQuery)) {
                onPage.accept(PageResult.failure(next.url(), next.depth(), -1, "Blocked by robots.txt"));
                continue;
            }

            politePause(Math.max(config.delayMillis(), robots.crawlDelayMillis()));
            PageResult page = scraper.scrape(next.url()).withDepth(next.depth());
            fetched++;
            onPage.accept(page);

            if (!page.isSuccess() || next.depth() >= config.maxDepth()) continue;
            for (String link : page.links()) {
                if (config.sameHostOnly() && !startHost.equalsIgnoreCase(URI.create(link).getHost())) continue;
                if (seen.add(link)) queue.add(new Pending(link, next.depth() + 1));
            }
        }
    }

    private RobotsTxt robotsFor(URI uri) {
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        return robotsCache.computeIfAbsent(origin, o -> {
            try {
                politePause(config.delayMillis());
                Connection.Response res = Jsoup.connect(o + "/robots.txt")
                        .userAgent(config.userAgent())
                        .timeout(config.timeoutMillis())
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .execute();
                int status = res.statusCode();
                if (status >= 200 && status < 300) return RobotsTxt.parse(res.body(), config.userAgent());
                // RFC 9309: 4xx means no restrictions. 5xx means assume complete disallow.
                return status >= 500 ? RobotsTxt.parse("User-agent: *\nDisallow: /", config.userAgent())
                                     : RobotsTxt.allowAll();
            } catch (IOException e) {
                // Host unreachable: let the page request itself report the real network error.
                return RobotsTxt.allowAll();
            }
        });
    }

    private void politePause(long delayMillis) {
        long wait = lastRequestAt + delayMillis - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
