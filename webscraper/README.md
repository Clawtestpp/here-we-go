# webscraper

A small, polite command-line web scraper and crawler in Java, built on
[jsoup](https://jsoup.org/).

For each page it extracts the title, meta description, `h1`–`h3` headings,
links and images, plus the text of anything matching an optional CSS selector.
It can crawl outward breadth-first, stays on the starting host by default,
obeys `robots.txt`, and waits between requests.

## Build

Requires Java 17+ and Maven.

```sh
mvn package          # runs the tests and builds target/webscraper.jar
```

## Usage

```sh
# Scrape one page, print JSON
java -jar target/webscraper.jar https://example.com

# Crawl two links deep (at most 20 pages), grab all product prices, save as CSV
java -jar target/webscraper.jar https://shop.example.com -d 2 -m 20 -s ".price" -o prices.csv
```

| Option | Meaning |
| --- | --- |
| `-d, --depth N` | how many links deep to crawl (default `0`: only the given URL) |
| `-m, --max-pages N` | stop after N pages (default `50`) |
| `-s, --select CSS` | also capture text of elements matching a CSS selector |
| `-f, --format json\|csv` | output format (default: from `--output` extension, else JSON) |
| `-o, --output FILE` | write results to a file instead of stdout |
| `--delay MS` | pause between requests (default `1000`) |
| `--timeout MS` | per-request timeout (default `10000`) |
| `--user-agent UA` | User-Agent header to send |
| `--all-hosts` | follow links to other hosts too |
| `--ignore-robots` | skip the `robots.txt` check |
| `-q, --quiet` | no progress output on stderr |

Progress is printed to stderr, results to stdout (or `--output`), so you can pipe
JSON straight into `jq`. The exit code is `0` if at least one page was scraped,
`1` if none were, and `2` for bad arguments.

## Using it as a library

```java
PageScraper scraper = new PageScraper("MyBot/1.0", 10_000, "article h2");
Crawler crawler = new Crawler(
        new Crawler.Options(1, 25, 500, true, true, "MyBot/1.0", 10_000), scraper);
for (PageResult page : crawler.crawl("https://example.com")) {
    System.out.println(page.title() + " -> " + page.selected());
}
```

## Layout

- `WebScraper` – CLI entry point and argument parsing
- `Crawler` – breadth-first crawl, de-duplication, delay, robots.txt cache
- `PageScraper` – fetches one URL and extracts data; URL normalisation
- `RobotsTxt` – `Allow`/`Disallow` matching with `*` and `$` support
- `ResultWriter` – JSON / CSV output
- `PageResult` – the data extracted from one page

Pages rendered entirely by JavaScript won't have their content in the HTML
jsoup sees; for those you'd need a headless browser such as Playwright.
