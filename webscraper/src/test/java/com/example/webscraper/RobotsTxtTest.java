package com.example.webscraper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RobotsTxtTest {

    private static final String UA = "WebScraper/1.0 (+https://example.com)";

    @Test
    void wildcardGroupApplies() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /admin
                Allow: /admin/public
                """, UA);
        assertTrue(robots.isAllowed("/"));
        assertFalse(robots.isAllowed("/admin"));
        assertFalse(robots.isAllowed("/admin/users"));
        assertTrue(robots.isAllowed("/admin/public/page"), "longer Allow beats shorter Disallow");
    }

    @Test
    void specificGroupReplacesWildcard() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /

                User-agent: OtherBot
                User-agent: webscraper
                Disallow: /tmp/
                """, UA);
        assertTrue(robots.isAllowed("/page"));
        assertFalse(robots.isAllowed("/tmp/file"));
    }

    @Test
    void wildcardsAndAnchors() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /*.pdf$
                Disallow: /search?*q=
                """, UA);
        assertFalse(robots.isAllowed("/docs/file.pdf"));
        assertTrue(robots.isAllowed("/docs/file.pdf.html"));
        assertFalse(robots.isAllowed("/search?lang=en&q=java"));
        assertTrue(robots.isAllowed("/search"));
    }

    @Test
    void emptyDisallowAndCommentsAllowEverything() {
        RobotsTxt robots = RobotsTxt.parse("# nothing to see\nUser-agent: *\nDisallow:   # all good\n", UA);
        assertTrue(robots.isAllowed("/anything"));
    }
}
