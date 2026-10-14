package com.example.scraper;

import java.util.List;
import java.util.Map;

/**
 * Everything extracted from a single fetched page.
 *
 * @param url         the URL that was requested
 * @param finalUrl    the URL after redirects
 * @param depth       crawl depth at which the page was found (0 = start URL)
 * @param statusCode  HTTP status code, or -1 if the request never completed
 * @param title       contents of the {@code <title>} element
 * @param description contents of {@code <meta name="description">}
 * @param headings    text of h1-h6 elements, keyed by tag name
 * @param links       absolute http(s) links found on the page, fragments removed, de-duplicated
 * @param images      absolute image URLs
 * @param selected    matches for the user-supplied CSS selector (text or attribute value)
 * @param error       error message if the page could not be fetched or parsed, otherwise null
 */
public record PageResult(
        String url,
        String finalUrl,
        int depth,
        int statusCode,
        String title,
        String description,
        Map<String, List<String>> headings,
        List<String> links,
        List<String> images,
        List<String> selected,
        String error) {

    public static PageResult failure(String url, int depth, int statusCode, String error) {
        return new PageResult(url, url, depth, statusCode, null, null,
                Map.of(), List.of(), List.of(), List.of(), error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public PageResult withDepth(int newDepth) {
        return new PageResult(url, finalUrl, newDepth, statusCode, title, description,
                headings, links, images, selected, error);
    }
}
