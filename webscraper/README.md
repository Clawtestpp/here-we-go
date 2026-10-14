# Java Web Scraper

A small, polite command-line web scraper and crawler written in Java 17+ using
[jsoup](https://jsoup.org/) for HTML parsing and [Gson](https://github.com/google/gson) for JSON output.

For every page it extracts:

- final URL (after redirects) and HTTP status
- `<title>` and `<meta name="description">`
- `h1`–`h6` headings
- all absolute `http(s)` links (de-duplicated, fragments stripped)
- image URLs
- optionally, whatever matches a CSS selector you supply (text or an attribute)

It can also crawl breadth-first to a given link depth. By default it is polite: it
honours `robots.txt` (Allow/Disallow, wildcards, `Crawl-delay`), waits 500 ms between
requests, stays on the start host and stops after 50 pages.

## Build

```bash
cd webscraper
mvn package          # runs the tests and builds target/webscraper-1.0.0.jar
```

## Usage

```bash
# Scrape a single page, human-readable output
java -jar target/webscraper-1.0.0.jar https://example.com

# Crawl one level deep and save JSON
java -jar target/webscraper-1.0.0.jar https://example.com -d 1 -f json -o pages.json

# Pull out specific elements with a CSS selector (here: story links on Hacker News)
java -jar target/webscraper-1.0.0.jar https://news.ycombinator.com -s ".titleline > a" -a href
```

| Option | Description |
| --- | --- |
| `-d, --depth <n>` | Link depth to crawl (default `0` = start page only) |
| `-m, --max-pages <n>` | Maximum pages to fetch (default `50`) |
| `-s, --selector <css>` | Also collect elements matching this CSS selector |
| `-a, --attr <name>` | With `--selector`, collect this attribute instead of the text |
| `-f, --format <fmt>` | `text` (default) or `json` |
| `-o, --output <file>` | Write to a file instead of stdout |
| `--delay <ms>` | Minimum delay between requests (default `500`) |
| `--timeout <ms>` | Per-request timeout (default `10000`) |
| `--user-agent <ua>` | User-Agent to send (also used to match `robots.txt` groups) |
| `--all-hosts` | Follow links to other hosts too |
| `--ignore-robots` | Don't consult `robots.txt` |

Exit codes: `0` all pages scraped, `1` bad arguments or I/O error, `2` at least one page failed
(HTTP error, network error, blocked by robots.txt, non-HTML content).

## Using it as a library

```java
ScraperConfig config = ScraperConfig.builder()
        .maxDepth(1)
        .selector("article h2 a")
        .attribute("href")
        .build();

// One page
PageResult page = new WebScraper(config).scrape("https://example.com");

// A crawl, streaming results as they arrive
new Crawler(config).crawl("https://example.com", p -> System.out.println(p.title()));
```

## Layout

```
src/main/java/com/example/scraper/
  ScraperCli.java     command-line entry point
  ScraperConfig.java  settings + builder
  WebScraper.java     fetches one page and extracts data
  Crawler.java        breadth-first crawl with robots.txt, delays and limits
  RobotsTxt.java      robots.txt parser (RFC 9309)
  PageResult.java     extracted data for one page
src/test/java/...     unit tests + end-to-end tests against a local HTTP server
```

Please scrape responsibly: respect each site's terms of service and keep request rates low.
