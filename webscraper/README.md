# webscraper

A small Python web scraper and crawler built on `requests` and `BeautifulSoup`.

For each page it extracts the **title**, **meta description**, **h1–h3 headings**,
**links**, **images** and (optionally) the text of anything matching a **CSS selector**.
It's polite by default: it checks `robots.txt`, waits between requests and stays on
the start host.

## Setup

```bash
pip install -r requirements.txt
```

## Command line

```bash
# One page, JSON to stdout
python -m webscraper https://example.com

# Crawl two levels deep, pull every <h2 class="title">, save as CSV
python -m webscraper https://example.com -d 2 -s "h2.title" -f csv -o results.csv

# Stream results as JSON Lines while crawling up to 100 pages
python -m webscraper https://example.com -d 3 -n 100 -f jsonl
```

| Option | Default | Meaning |
| --- | --- | --- |
| `-d, --depth` | `0` | How many link levels to follow (0 = start page only) |
| `-n, --max-pages` | `50` | Stop after this many fetches |
| `-s, --selector` | – | CSS selector whose text goes into `matches` |
| `-f, --format` | `json` | `json`, `jsonl` or `csv` |
| `-o, --output` | stdout | Output file |
| `--delay` | `1` | Seconds between requests |
| `--timeout` | `10` | Request timeout in seconds |
| `--user-agent` | built-in | User-Agent header |
| `--all-hosts` | off | Follow links to other domains |
| `--ignore-robots` | off | Skip robots.txt checks |

Progress goes to stderr, so you can pipe the data from stdout.

## As a library

```python
from webscraper import Scraper, Crawler, CrawlOptions

page = Scraper().scrape("https://example.com", selector="p")
print(page.title, page.matches)

for page in Crawler(Scraper(), CrawlOptions(max_depth=1)).crawl("https://example.com"):
    print(page.url, page.status, page.error)
```

Failed fetches don't raise. They come back as a `PageData` with `error` set
(for example `HTTP 404`, `Not HTML: application/pdf`, `Blocked by robots.txt`).

## Tests

```bash
python -m unittest -v
```

The tests run against a local HTTP server, so they don't need internet access.

Only scrape sites you're allowed to, and respect their terms of service.
