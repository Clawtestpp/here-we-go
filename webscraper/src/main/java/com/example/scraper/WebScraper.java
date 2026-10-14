package com.example.scraper;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Selector;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fetches a single page and extracts structured data from it. */
public class WebScraper {

    private final ScraperConfig config;

    public WebScraper(ScraperConfig config) {
        this.config = config;
    }

    /** Downloads {@code url} and extracts its data. Never throws; failures are reported in the result. */
    public PageResult scrape(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(config.userAgent())
                    .timeout(config.timeoutMillis())
                    .maxBodySize(config.maxBodyBytes())
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();

            int status = response.statusCode();
            if (status >= 400) {
                return PageResult.failure(url, 0, status, "HTTP " + status + " " + response.statusMessage());
            }
            Document doc = response.parse();
            return extract(url, response.url().toString(), status, doc);
        } catch (UnsupportedMimeTypeException e) {
            return PageResult.failure(url, 0, -1, "Unsupported content type: " + e.getMimeType());
        } catch (HttpStatusException e) {
            return PageResult.failure(url, 0, e.getStatusCode(), "HTTP " + e.getStatusCode());
        } catch (IOException | IllegalArgumentException e) {
            return PageResult.failure(url, 0, -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Extracts data from an already-parsed document. Exposed for testing and offline use. */
    public PageResult extract(String requestedUrl, String finalUrl, int status, Document doc) {
        // doc.title() only looks in <head>; fall back to a stray <title> elsewhere in malformed pages.
        String title = blankToNull(doc.title());
        if (title == null) {
            Element anyTitle = doc.selectFirst("title");
            title = anyTitle == null ? null : blankToNull(anyTitle.text());
        }

        Element metaDesc = doc.selectFirst("meta[name=description]");
        String description = metaDesc == null ? null : blankToNull(metaDesc.attr("content"));

        Map<String, List<String>> headings = new LinkedHashMap<>();
        for (Element h : doc.select("h1, h2, h3, h4, h5, h6")) {
            String text = h.text().trim();
            if (!text.isEmpty()) {
                headings.computeIfAbsent(h.tagName(), k -> new ArrayList<>()).add(text);
            }
        }

        Set<String> links = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            String normalized = normalizeUrl(a.absUrl("href"));
            if (normalized != null) links.add(normalized);
        }

        Set<String> images = new LinkedHashSet<>();
        for (Element img : doc.select("img[src]")) {
            String src = img.absUrl("src");
            if (!src.isEmpty()) images.add(src);
        }

        List<String> selected = new ArrayList<>();
        if (config.selector() != null && !config.selector().isBlank()) {
            try {
                for (Element el : doc.select(config.selector())) {
                    String value;
                    if (config.attribute() == null) {
                        value = el.text();
                    } else {
                        // Resolve URL-valued attributes (href, src, ...) to absolute form when possible.
                        String abs = el.absUrl(config.attribute());
                        value = abs.isEmpty() ? el.attr(config.attribute()) : abs;
                    }
                    if (!value.isBlank()) selected.add(value.trim());
                }
            } catch (Selector.SelectorParseException e) {
                return new PageResult(requestedUrl, finalUrl, 0, status, title, description, headings,
                        List.copyOf(links), List.copyOf(images), List.of(),
                        "Invalid CSS selector: " + e.getMessage());
            }
        }

        return new PageResult(requestedUrl, finalUrl, 0, status, title, description, headings,
                List.copyOf(links), List.copyOf(images), selected, null);
    }

    /**
     * Returns an absolute http(s) URL with its fragment removed, or null if the input is not
     * a crawlable web URL (mailto:, javascript:, malformed, ...).
     */
    static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) return null;
            scheme = scheme.toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? "/" : uri.getRawPath();
            int port = uri.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) port = -1;
            StringBuilder sb = new StringBuilder()
                    .append(scheme).append("://").append(uri.getHost().toLowerCase());
            if (port != -1) sb.append(':').append(port);
            sb.append(path);
            if (uri.getRawQuery() != null) sb.append('?').append(uri.getRawQuery());
            return sb.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
