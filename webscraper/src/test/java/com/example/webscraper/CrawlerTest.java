package com.example.webscraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Crawls a tiny site served from a local HTTP server. */
class CrawlerTest {

    private HttpServer server;
    private String base;
    private final Map<String, Integer> hits = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        page("/", """
                <html><head><title>Home</title>
                <meta name="description" content="The home page"></head>
                <body><h1>Welcome</h1>
                <a href="/a">A</a> <a href="b">B</a> <a href="/a#again">A again</a>
                <a href="/private/secret">Secret</a>
                <a href="mailto:me@example.com">Mail</a>
                <a href="https://elsewhere.invalid/">Elsewhere</a>
                <img src="/logo.png">
                <p class="price">$10</p><p class="price">$20</p>
                </body></html>""");
        page("/a", "<html><head><title>A</title></head><body><h2>Page A</h2><a href='/deep'>deep</a></body></html>");
        page("/b", "<html><head><title>B</title></head><body><a href='/'>home</a></body></html>");
        page("/deep", "<html><head><title>Deep</title></head><body></body></html>");
        page("/private/secret", "<html><head><title>Secret</title></head></html>");
        server.createContext("/robots.txt", exchange -> respond(exchange, 200, "text/plain",
                "User-agent: *\nDisallow: /private/\n"));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void depthZeroScrapesOnlyTheStartPage() throws Exception {
        List<PageResult> results = crawler(0).crawl(base + "/");

        assertEquals(1, results.size());
        PageResult home = results.get(0);
        assertNull(home.error());
        assertEquals(200, home.status());
        assertEquals("Home", home.title());
        assertEquals("The home page", home.description());
        assertEquals(List.of("Welcome"), home.headings());
        assertEquals(List.of(base + "/logo.png"), home.images());
        assertEquals(List.of("$10", "$20"), home.selected());
        // Fragments collapse, relative links resolve, mailto: is dropped.
        assertEquals(List.of(base + "/a", base + "/b", base + "/private/secret", "https://elsewhere.invalid/"),
                home.links());
    }

    @Test
    void followsLinksBreadthFirstAndHonoursRobots() throws Exception {
        List<PageResult> results = crawler(2).crawl(base);

        List<String> urls = results.stream().map(PageResult::url).toList();
        assertEquals(List.of(base + "/", base + "/a", base + "/b", base + "/private/secret", base + "/deep"), urls);

        PageResult secret = results.get(3);
        assertEquals("Blocked by robots.txt", secret.error());
        assertFalse(hits.containsKey("/private/secret"), "robots-blocked page must not be fetched");
        // Every page is fetched once even though several pages link to "/".
        assertEquals(1, hits.get("/"));
    }

    @Test
    void respectsMaxPages() throws Exception {
        Crawler crawler = new Crawler(new Crawler.Options(5, 2, 0, true, true, "Test", 5000),
                new PageScraper("Test", 5000, null));
        assertEquals(2, crawler.crawl(base).size());
    }

    @Test
    void reportsHttpErrors() throws Exception {
        PageResult missing = crawler(0).crawl(base + "/nope").get(0);
        assertEquals(404, missing.status());
        assertTrue(missing.error().contains("404"));
    }

    private Crawler crawler(int depth) {
        return new Crawler(new Crawler.Options(depth, 50, 0, true, true, "Test", 5000),
                new PageScraper("Test", 5000, ".price"));
    }

    private void page(String path, String html) {
        server.createContext(path, exchange -> {
            String requested = exchange.getRequestURI().getPath();
            if (!requested.equals(path)) {
                respond(exchange, 404, "text/html", "<html><title>Not found</title></html>");
                return;
            }
            hits.merge(path, 1, Integer::sum);
            respond(exchange, 200, "text/html; charset=utf-8", html);
        });
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
