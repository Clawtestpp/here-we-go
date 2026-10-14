package com.example.webscraper;

import java.util.List;

/**
 * Everything scraped from a single URL.
 *
 * @param url         the final URL after redirects
 * @param depth       how many links away from the start URL this page was found
 * @param status      HTTP status code, or 0 if the request never completed
 * @param title       contents of the {@code <title>} element
 * @param description contents of {@code <meta name="description">}
 * @param headings    text of every h1-h3 element, in document order
 * @param links       absolute http(s) links found on the page, de-duplicated
 * @param images      absolute image URLs found on the page, de-duplicated
 * @param selected    text of elements matching the user's CSS selector, if one was given
 * @param error       why the page could not be scraped, or null on success
 */
public record PageResult(
        String url,
        int depth,
        int status,
        String title,
        String description,
        List<String> headings,
        List<String> links,
        List<String> images,
        List<String> selected,
        String error) {

    public PageResult {
        headings = List.copyOf(headings);
        links = List.copyOf(links);
        images = List.copyOf(images);
        selected = List.copyOf(selected);
    }

    static PageResult failure(String url, int depth, int status, String error) {
        return new PageResult(url, depth, status, "", "", List.of(), List.of(), List.of(), List.of(), error);
    }

    public boolean ok() {
        return error == null;
    }
}
