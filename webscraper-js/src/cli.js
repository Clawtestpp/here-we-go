#!/usr/bin/env node
// Command-line interface: node src/cli.js URL [options]
import { createWriteStream, realpathSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import { Crawler, DEFAULT_USER_AGENT, Scraper } from "./scraper.js";

const HELP = `Usage: webscraper <url> [options]

  -d, --depth N         link depth to follow (default 0, start page only)
  -n, --max-pages N     maximum pages to fetch (default 50)
  -s, --selector CSS    CSS selector whose matching text to extract
  -f, --format FMT      json | jsonl | csv (default json)
  -o, --output FILE     write to FILE instead of stdout
      --delay SECONDS   wait between requests (default 1)
      --timeout SECONDS request timeout (default 10)
      --user-agent UA   User-Agent header to send
      --all-hosts       follow links to other hosts too
      --ignore-robots   don't check robots.txt
  -h, --help            show this help`;

const CSV_FIELDS = ["url", "status", "title", "description", "headings", "links", "images", "matches", "error"];

const csvCell = (v) => {
  const s = Array.isArray(v) ? v.join(" | ") : v == null ? "" : String(v);
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};
export const toCsvRow = (page) => CSV_FIELDS.map((f) => csvCell(page[f])).join(",");

const WRITERS = {
  async json(pages, out) {
    const all = [];
    for await (const p of pages) all.push(p);
    out.write(JSON.stringify(all, null, 2) + "\n");
  },
  async jsonl(pages, out) {
    for await (const p of pages) out.write(JSON.stringify(p) + "\n");
  },
  async csv(pages, out) {
    out.write(CSV_FIELDS.join(",") + "\r\n");
    for await (const p of pages) out.write(toCsvRow(p) + "\r\n");
  },
};

function fail(msg) {
  console.error(`error: ${msg}\n\n${HELP}`);
  process.exit(2);
}

async function main() {
  let parsed;
  try {
    parsed = parseArgs({
      allowPositionals: true,
      options: {
        depth: { type: "string", short: "d", default: "0" },
        "max-pages": { type: "string", short: "n", default: "50" },
        selector: { type: "string", short: "s" },
        format: { type: "string", short: "f", default: "json" },
        output: { type: "string", short: "o" },
        delay: { type: "string", default: "1" },
        timeout: { type: "string", default: "10" },
        "user-agent": { type: "string", default: DEFAULT_USER_AGENT },
        "all-hosts": { type: "boolean", default: false },
        "ignore-robots": { type: "boolean", default: false },
        help: { type: "boolean", short: "h", default: false },
      },
    });
  } catch (err) {
    fail(err.message);
  }
  const { values: o, positionals } = parsed;
  if (o.help) return console.log(HELP);
  if (positionals.length !== 1) fail("expected exactly one URL");
  if (!WRITERS[o.format]) fail(`unknown format: ${o.format}`);

  const crawler = new Crawler(
    new Scraper({ userAgent: o["user-agent"], timeout: Number(o.timeout) * 1000 }),
    {
      maxDepth: Number(o.depth),
      maxPages: Number(o["max-pages"]),
      delay: Number(o.delay) * 1000,
      sameHost: !o["all-hosts"],
      respectRobots: !o["ignore-robots"],
      selector: o.selector ?? null,
    },
  );

  async function* progress(pages) {
    let i = 0;
    for await (const page of pages) {
      console.error(`[${++i}] ${page.url} -> ${page.error ?? page.status}`);
      yield page;
    }
  }

  const out = o.output ? createWriteStream(o.output, "utf8") : process.stdout;
  try {
    await WRITERS[o.format](progress(crawler.crawl(positionals[0])), out);
  } catch (err) {
    fail(err.message);
  }
  if (o.output) out.end();
}

// Run only when executed directly, not when imported (e.g. by tests).
if (process.argv[1] && realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) main();
