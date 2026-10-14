import io
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from webscraper import CrawlOptions, Crawler, Scraper, parse_html
from webscraper.cli import write_csv

PAGES = {
    "/": """<html><head><title> Home </title>
        <meta name="description" content="The home page"></head>
        <body><h1>Welcome</h1><h2 class="item">First</h2><h2 class="item">Second</h2>
        <a href="/a#top">A</a><a href="/b">B</a><a href="/private/x">P</a>
        <a href="https://elsewhere.example/">Ext</a><a href="mailto:x@y.z">Mail</a>
        <img src="/logo.png"></body></html>""",
    "/a": '<html><head><title>A</title></head><body><a href="/c">C</a></body></html>',
    "/b": "<html><head><title>B</title></head><body></body></html>",
    "/c": "<html><head><title>C</title></head><body></body></html>",
    "/private/x": "<html><head><title>Secret</title></head></html>",
}
ROBOTS = "User-agent: *\nDisallow: /private/\n"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/robots.txt":
            body, ctype, code = ROBOTS, "text/plain", 200
        elif self.path in PAGES:
            body, ctype, code = PAGES[self.path], "text/html; charset=utf-8", 200
        else:
            body, ctype, code = "nope", "text/plain", 404
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


class ParseTests(unittest.TestCase):
    def test_extracts_fields(self):
        page = parse_html(PAGES["/"], "http://site.test/", selector="h2.item")
        self.assertEqual(page.title, "Home")
        self.assertEqual(page.description, "The home page")
        self.assertEqual(page.headings, ["Welcome", "First", "Second"])
        self.assertEqual(page.matches, ["First", "Second"])
        self.assertEqual(page.images, ["http://site.test/logo.png"])
        # fragments stripped, relative links resolved, mailto dropped
        self.assertEqual(
            page.links,
            [
                "http://site.test/a",
                "http://site.test/b",
                "http://site.test/private/x",
                "https://elsewhere.example/",
            ],
        )

    def test_csv_output(self):
        buf = io.StringIO()
        write_csv([parse_html(PAGES["/"], "http://site.test/")], buf)
        lines = buf.getvalue().splitlines()
        self.assertTrue(lines[0].startswith("url,status,title"))
        self.assertIn("Welcome | First | Second", lines[1])


class CrawlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.base = f"http://127.0.0.1:{cls.server.server_port}"
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def crawl(self, **opts):
        scraper = Scraper(timeout=5)
        scraper.session.trust_env = False  # don't route localhost through a proxy
        return list(Crawler(scraper, CrawlOptions(delay=0, **opts)).crawl(self.base + "/"))

    def test_depth_zero_fetches_only_start(self):
        pages = self.crawl(max_depth=0)
        self.assertEqual([p.title for p in pages], ["Home"])

    def test_crawl_respects_depth_host_and_robots(self):
        pages = {p.url.removeprefix(self.base): p for p in self.crawl(max_depth=2)}
        self.assertEqual(set(pages), {"/", "/a", "/b", "/private/x", "/c"})
        self.assertEqual(pages["/private/x"].error, "Blocked by robots.txt")
        self.assertEqual(pages["/c"].title, "C")

    def test_ignore_robots_and_max_pages(self):
        pages = self.crawl(max_depth=2, respect_robots=False, max_pages=4)
        self.assertEqual(len(pages), 4)
        self.assertIn("Secret", [p.title for p in pages])

    def test_http_error_recorded(self):
        scraper = Scraper(timeout=5)
        scraper.session.trust_env = False
        page = scraper.scrape(self.base + "/missing")
        self.assertEqual(page.status, 404)
        self.assertEqual(page.error, "HTTP 404")
        json.dumps(page.to_dict())  # serialisable


if __name__ == "__main__":
    unittest.main()
