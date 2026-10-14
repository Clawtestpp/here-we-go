# webscraper-js

The JavaScript (Node.js 18+) version of [`../webscraper`](../webscraper), built on
the built-in `fetch`, [cheerio](https://cheerio.js.org/) and
[robots-parser](https://www.npmjs.com/package/robots-parser).

It extracts the same fields (title, meta description, h1–h3 headings, links, images,
CSS-selector matches) and has the same polite defaults: robots.txt, a delay between
requests, and same-host only.

## Setup

```bash
npm install
```

## Command line

```bash
node src/cli.js https://example.com
node src/cli.js https://example.com -d 2 -s "h2.title" -f csv -o results.csv
node src/cli.js https://example.com -d 3 -n 100 -f jsonl
node src/cli.js --help
```

The options match the Python CLI: `-d/--depth`, `-n/--max-pages`, `-s/--selector`,
`-f/--format json|jsonl|csv`, `-o/--output`, `--delay`, `--timeout` (both in seconds),
`--user-agent`, `--all-hosts`, `--ignore-robots`.

## As a library

```js
import { Scraper, Crawler } from "./src/scraper.js";

const page = await new Scraper().scrape("https://example.com", "p");
console.log(page.title, page.matches);

for await (const p of new Crawler(new Scraper(), { maxDepth: 1 }).crawl("https://example.com")) {
  console.log(p.url, p.status, p.error);
}
```

Failed fetches don't throw. They come back as a page object with `error` set.

## Tests

```bash
npm test
```

The tests run against a local HTTP server.
