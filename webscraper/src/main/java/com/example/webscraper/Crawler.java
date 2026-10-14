package com.example.webscraper;

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

import org.jsoup.Connection;
import org.jsoup.Jsoup;

/**
 * Breadth-first crawler. Starting from one URL it scrapes each page, then follows the
 * links it finds up to {@link Options#maxDepth()} hops away, stopping after
 * {@link Options#maxPages()} pages. It waits {@link Options#delayMillis()} between
 * requests and, unless told otherwise, obeys robots.txt and stays on the start host.
 */
public final class Crawler {

    public record Options(
            int maxDepth,
            int maxPages,
            long delayMillis,
            boolean sameHostOnly,
            boolean respectRobots,
            String userAgent,
            int timeoutMillis) {

        public Options {
            if (maxDepth < 0) {
                throw new IllegalArgumentException("maxDepth must be >= 0");
            }
            if (maxPages < 1) {
                throw new IllegalArgumentException("maxPages must be >= 1");
            }
            if (delayMillis < 0) {
                throw new IllegalArgumentException("delayMillis must be >= 0");
            }
        }
    }

    private record Pending(String url, int depth) { }

    private final Options options;
    private final PageScraper scraper;
    private final Map<String, RobotsTxt> robotsByOrigin = new HashMap<>();

    public Crawler(Options options, PageScraper scraper) {
        this.options = options;
        this.scraper = scraper;
    }

    public List<PageResult> crawl(String startUrl) throws InterruptedException {
        return crawl(startUrl, result -> { });
    }

    /**
     * @param onPage called after each page is scraped (or skipped), e.g. to report progress
     */
    public List<PageResult> crawl(String startUrl, Consumer<PageResult> onPage) throws InterruptedException {
        String start = PageScraper.normalize(startUrl);
        if (start == null) {
            throw new IllegalArgumentException("Not an http(s) URL: " + startUrl);
        }
        String startHost = URI.create(start).getHost();

        List<PageResult> results = new ArrayList<>();
        Deque<Pending> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(new Pending(start, 0));
        seen.add(start);

        boolean firstRequest = true;
        while (!queue.isEmpty() && results.size() < options.maxPages()) {
            Pending next = queue.poll();
            URI uri = URI.create(next.url());

            if (options.respectRobots() && !robotsFor(uri).isAllowed(pathAndQuery(uri))) {
                PageResult blocked = PageResult.failure(next.url(), next.depth(), 0, "Blocked by robots.txt");
                results.add(blocked);
                onPage.accept(blocked);
                continue;
            }

            if (!firstRequest && options.delayMillis() > 0) {
                Thread.sleep(options.delayMillis());
            }
            firstRequest = false;

            PageResult page = scraper.scrape(next.url(), next.depth());
            results.add(page);
            onPage.accept(page);
            // Redirects can land us on a new URL; don't fetch it again later.
            String landed = PageScraper.normalize(page.url());
            if (landed != null) {
                seen.add(landed);
            }

            if (page.ok() && next.depth() < options.maxDepth()) {
                for (String link : page.links()) {
                    if (options.sameHostOnly() && !startHost.equalsIgnoreCase(URI.create(link).getHost())) {
                        continue;
                    }
                    if (seen.add(link)) {
                        queue.add(new Pending(link, next.depth() + 1));
                    }
                }
            }
        }
        return results;
    }

    private RobotsTxt robotsFor(URI uri) {
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        return robotsByOrigin.computeIfAbsent(origin, this::fetchRobots);
    }

    private RobotsTxt fetchRobots(String origin) {
        try {
            Connection.Response response = Jsoup.connect(origin + "/robots.txt")
                    .userAgent(options.userAgent())
                    .timeout(options.timeoutMillis())
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();
            if (response.statusCode() == 200) {
                return RobotsTxt.parse(response.body(), options.userAgent());
            }
        } catch (IOException e) {
            // Unreachable robots.txt: treat the site as unrestricted.
        }
        return RobotsTxt.allowAll();
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }
}
