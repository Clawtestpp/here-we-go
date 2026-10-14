package com.example.scraper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RobotsTxtTest {

    private static final String UA = ScraperConfig.DEFAULT_USER_AGENT;

    @Test
    void emptyAllowsEverything() {
        assertTrue(RobotsTxt.parse("", UA).isAllowed("/anything"));
    }

    @Test
    void wildcardGroupDisallowsPrefix() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: *
                Disallow: /private
                """, UA);
        assertFalse(r.isAllowed("/private/page"));
        assertTrue(r.isAllowed("/public"));
    }

    @Test
    void longestMatchWinsAndAllowWinsTies() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: *
                Disallow: /docs
                Allow: /docs/public
                Disallow: /tie
                Allow: /tie
                """, UA);
        assertFalse(r.isAllowed("/docs/secret"));
        assertTrue(r.isAllowed("/docs/public/a"));
        assertTrue(r.isAllowed("/tie"));
    }

    @Test
    void specificAgentGroupOverridesWildcard() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: *
                Disallow: /

                User-agent: JavaWebScraper
                Disallow: /admin
                Crawl-delay: 2
                """, UA);
        assertTrue(r.isAllowed("/home"));
        assertFalse(r.isAllowed("/admin"));
        assertEquals(2000, r.crawlDelayMillis());
    }

    @Test
    void wildcardsAndEndAnchor() {
        assertTrue(RobotsTxt.matches("/*.pdf$", "/files/report.pdf"));
        assertFalse(RobotsTxt.matches("/*.pdf$", "/files/report.pdf?x=1"));
        assertTrue(RobotsTxt.matches("/*/edit", "/page/edit/now"));
        assertFalse(RobotsTxt.matches("/a$", "/ab"));
    }

    @Test
    void emptyDisallowAllowsAll() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow:\n", UA);
        assertTrue(r.isAllowed("/x"));
    }
}
