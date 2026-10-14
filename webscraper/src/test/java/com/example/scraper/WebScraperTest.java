package com.example.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebScraperTest {

    private static final String HTML = """
            <html><head>
              <title> Test Page </title>
              <meta name="description" content="A page for tests">
            </head><body>
              <h1>Main</h1><h2>Sub one</h2><h2>Sub two</h2><h3>  </h3>
              <a href="/about#team">About</a>
              <a href="/about">About again</a>
              <a href="https://other.example.org/x">External</a>
              <a href="mailto:a@b.c">Mail</a>
              <a href="javascript:void(0)">JS</a>
              <img src="img/logo.png">
              <ul><li class="item"><a href="/p/1">One</a></li><li class="item"><a href="/p/2">Two</a></li></ul>
            </body></html>
            """;

    private static Document doc() {
        return Jsoup.parse(HTML, "https://example.com/dir/index.html");
    }

    @Test
    void extractsBasics() {
        PageResult r = new WebScraper(ScraperConfig.defaults())
                .extract("https://example.com/dir/index.html", "https://example.com/dir/index.html", 200, doc());

        assertTrue(r.isSuccess());
        assertEquals("Test Page", r.title());
        assertEquals("A page for tests", r.description());
        assertEquals(List.of("Main"), r.headings().get("h1"));
        assertEquals(List.of("Sub one", "Sub two"), r.headings().get("h2"));
        assertNull(r.headings().get("h3"), "blank headings are skipped");
        assertEquals(List.of(
                "https://example.com/about",
                "https://other.example.org/x",
                "https://example.com/p/1",
                "https://example.com/p/2"), r.links());
        assertEquals(List.of("https://example.com/dir/img/logo.png"), r.images());
    }

    @Test
    void selectorCollectsTextOrAttribute() {
        ScraperConfig text = ScraperConfig.builder().selector("li.item a").build();
        assertEquals(List.of("One", "Two"),
                new WebScraper(text).extract("u", "u", 200, doc()).selected());

        ScraperConfig attr = ScraperConfig.builder().selector("li.item a").attribute("href").build();
        assertEquals(List.of("https://example.com/p/1", "https://example.com/p/2"),
                new WebScraper(attr).extract("u", "u", 200, doc()).selected());
    }

    @Test
    void invalidSelectorIsReportedNotThrown() {
        ScraperConfig bad = ScraperConfig.builder().selector("a[").build();
        PageResult r = new WebScraper(bad).extract("u", "u", 200, doc());
        assertFalse(r.isSuccess());
        assertTrue(r.error().startsWith("Invalid CSS selector"));
    }

    @Test
    void normalizeUrl() {
        assertEquals("https://example.com/", WebScraper.normalizeUrl("HTTPS://Example.COM"));
        assertEquals("http://example.com/a?b=1", WebScraper.normalizeUrl("http://example.com:80/a?b=1#frag"));
        assertEquals("http://example.com:8080/", WebScraper.normalizeUrl("http://example.com:8080"));
        assertNull(WebScraper.normalizeUrl("ftp://example.com/file"));
        assertNull(WebScraper.normalizeUrl("mailto:someone@example.com"));
        assertNull(WebScraper.normalizeUrl("not a url"));
    }
}
