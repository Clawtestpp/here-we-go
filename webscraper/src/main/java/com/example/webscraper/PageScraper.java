package com.example.webscraper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.QueryParser;

/** Fetches one URL and extracts structured data from the HTML. */
public final class PageScraper {

    private final String userAgent;
    private final int timeoutMillis;
    private final String selector;

    /**
     * @param userAgent     User-Agent header sent with every request
     * @param timeoutMillis connect + read timeout
     * @param selector      optional CSS selector whose matches are captured; may be null
     */
    public PageScraper(String userAgent, int timeoutMillis, String selector) {
        this.userAgent = userAgent;
        this.timeoutMillis = timeoutMillis;
        this.selector = selector == null || selector.isBlank() ? null : selector;
        if (this.selector != null) {
            // Fail fast on a bad selector instead of on every page.
            QueryParser.parse(this.selector);
        }
    }

    public PageResult scrape(String url, int depth) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMillis)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();
            String finalUrl = response.url().toString();
            int status = response.statusCode();
            if (status >= 400) {
                return PageResult.failure(finalUrl, depth, status, "HTTP " + status);
            }
            return parse(response.parse(), finalUrl, depth, status);
        } catch (UnsupportedMimeTypeException e) {
            return PageResult.failure(url, depth, 0, "Not HTML: " + e.getMimeType());
        } catch (HttpStatusException e) {
            return PageResult.failure(url, depth, e.getStatusCode(), "HTTP " + e.getStatusCode());
        } catch (IOException | IllegalArgumentException e) {
            return PageResult.failure(url, depth, 0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Extracts data from an already-parsed document. Exposed for testing and offline use. */
    public PageResult parse(Document doc, String url, int depth, int status) {
        List<String> headings = new ArrayList<>();
        for (Element h : doc.select("h1, h2, h3")) {
            String text = h.text();
            if (!text.isEmpty()) {
                headings.add(text);
            }
        }

        Set<String> links = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            String normalized = normalize(a.absUrl("href"));
            if (normalized != null) {
                links.add(normalized);
            }
        }

        Set<String> images = new LinkedHashSet<>();
        for (Element img : doc.select("img[src]")) {
            String src = img.absUrl("src");
            if (!src.isEmpty()) {
                images.add(src);
            }
        }

        List<String> selected = new ArrayList<>();
        if (selector != null) {
            for (Element el : doc.select(selector)) {
                selected.add(el.text());
            }
        }

        String description = doc.select("meta[name=description]").attr("content");

        return new PageResult(url, depth, status, doc.title(), description,
                headings, List.copyOf(links), List.copyOf(images), selected, null);
    }

    /**
     * Canonicalises a URL for crawling: http(s) only, fragment dropped, scheme and host
     * lower-cased, empty path turned into "/". Returns null for anything that is not a
     * crawlable web URL (mailto:, javascript:, malformed, ...).
     */
    static String normalize(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            int port = uri.getPort();
            boolean defaultPort = port == -1
                    || (scheme.equals("http") && port == 80)
                    || (scheme.equals("https") && port == 443);
            StringBuilder sb = new StringBuilder()
                    .append(scheme).append("://")
                    .append(uri.getHost().toLowerCase(Locale.ROOT));
            if (!defaultPort) {
                sb.append(':').append(port);
            }
            sb.append(path);
            if (uri.getRawQuery() != null) {
                sb.append('?').append(uri.getRawQuery());
            }
            return sb.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
