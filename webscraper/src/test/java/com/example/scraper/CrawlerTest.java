package com.example.scraper;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests against a throwaway local HTTP server. */
class CrawlerTest {

    private HttpServer server;
    private String base;

    private static final Map<String, String> PAGES = Map.of(
            "/", """
                 <title>Home</title>
                 <a href="/a">A</a> <a href="/b">B</a> <a href="/secret/x">S</a>
                 <a href="https://elsewhere.invalid/">Ext</a> <a href="/missing">404</a>
                 """,
            "/a", "<title>A</title><a href=\"/c\">C</a><a href=\"/\">Home</a>",
            "/b", "<title>B</title>",
            "/c", "<title>C</title>",
            "/secret/x", "<title>Secret</title>");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            String type = "text/html; charset=utf-8";
            int status = 200;
            if (path.equals("/robots.txt")) {
                body = "User-agent: *\nDisallow: /secret\n";
                type = "text/plain";
            } else if (PAGES.containsKey(path)) {
                body = "<html><body>" + PAGES.get(path) + "</body></html>";
            } else {
                body = "not found";
                status = 404;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ScraperConfig.Builder fast() {
        return ScraperConfig.builder().delayMillis(0).timeoutMillis(5000);
    }

    @Test
    void depthZeroFetchesOnlyStartPage() {
        List<PageResult> pages = new Crawler(fast().build()).crawl(base + "/");
        assertEquals(1, pages.size());
        assertEquals("Home", pages.get(0).title());
        assertEquals(200, pages.get(0).statusCode());
    }

    @Test
    void crawlsBreadthFirstRespectingRobotsAndHost() {
        List<PageResult> pages = new Crawler(fast().maxDepth(2).build()).crawl(base + "/");

        List<String> urls = pages.stream().map(PageResult::url).toList();
        assertEquals(List.of(
                base + "/", base + "/a", base + "/b", base + "/secret/x", base + "/missing", base + "/c"), urls);

        PageResult secret = pages.get(3);
        assertEquals("Blocked by robots.txt", secret.error());

        PageResult missing = pages.get(4);
        assertEquals(404, missing.statusCode());
        assertFalse(missing.isSuccess());

        assertEquals(2, pages.get(5).depth());
        assertTrue(urls.stream().noneMatch(u -> u.contains("elsewhere.invalid")));
    }

    @Test
    void ignoreRobotsAndMaxPages() {
        List<PageResult> pages = new Crawler(fast().maxDepth(1).respectRobots(false).maxPages(4).build())
                .crawl(base + "/");
        assertEquals(4, pages.size());
        assertEquals("Secret", pages.get(3).title());
    }

    @Test
    void cliWritesJson() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = ScraperCli.run(new String[]{base + "/", "-f", "json", "--delay", "0", "-s", "a", "-a", "href"},
                new PrintStream(out), new PrintStream(err));
        String json = out.toString(StandardCharsets.UTF_8);
        assertEquals(0, code, err.toString());
        assertTrue(json.trim().startsWith("["));
        assertTrue(json.contains("\"title\": \"Home\""));
        assertTrue(json.contains(base + "/secret/x"));
    }

    @Test
    void cliRejectsBadArguments() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(1, ScraperCli.run(new String[]{}, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err)));
        assertEquals(1, ScraperCli.run(new String[]{"http://x", "--depth", "abc"},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(err)));
        assertTrue(err.toString().contains("expects a number"));
    }
}
