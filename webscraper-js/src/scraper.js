// Fetch pages and extract structured data; crawl breadth-first, politely.
import * as cheerio from "cheerio";
import robotsParser from "robots-parser";

export const DEFAULT_USER_AGENT =
  "here-we-go-webscraper-js/1.0 (+https://github.com/clawtestpp/here-we-go)";

/** Drop the #fragment; return null for anything that isn't http(s). */
export function normalizeUrl(href, base) {
  try {
    const url = new URL(href, base);
    if (url.protocol !== "http:" && url.protocol !== "https:") return null;
    url.hash = "";
    return url.href;
  } catch {
    return null;
  }
}

function emptyPage(url, extra = {}) {
  return {
    url, status: null, title: "", description: "",
    headings: [], links: [], images: [], matches: [], error: null,
    ...extra,
  };
}

const text = (el, $) => $(el).text().replace(/\s+/g, " ").trim();

/** Extract title, meta description, headings, links, images and optional CSS matches. */
export function parseHtml(html, baseUrl, selector = null) {
  const $ = cheerio.load(html);
  const page = emptyPage(baseUrl);

  page.title = $("title").first().text().trim();
  page.description = ($('meta[name="description"]').attr("content") ?? "").trim();
  page.headings = $("h1, h2, h3").map((_, el) => text(el, $)).get().filter(Boolean);

  const links = new Set();
  $("a[href]").each((_, a) => {
    const link = normalizeUrl($(a).attr("href"), baseUrl);
    if (link) links.add(link);
  });
  page.links = [...links];

  const images = new Set();
  $("img[src]").each((_, img) => {
    const src = normalizeUrl($(img).attr("src"), baseUrl);
    if (src) images.add(src);
  });
  page.images = [...images];

  if (selector) page.matches = $(selector).map((_, el) => text(el, $)).get();
  return page;
}

export class Scraper {
  constructor({ userAgent = DEFAULT_USER_AGENT, timeout = 10_000 } = {}) {
    this.userAgent = userAgent;
    this.timeout = timeout;
  }

  get(url) {
    return fetch(url, {
      headers: { "User-Agent": this.userAgent },
      redirect: "follow",
      signal: AbortSignal.timeout(this.timeout),
    });
  }

  /** Fetch and parse one page. Network and HTTP problems are recorded in `error`. */
  async scrape(url, selector = null) {
    let res;
    try {
      res = await this.get(url);
    } catch (err) {
      return emptyPage(url, { error: `${err.name}: ${err.cause?.message ?? err.message}` });
    }
    const contentType = res.headers.get("content-type") ?? "";
    if (res.status >= 400) {
      return emptyPage(res.url, { status: res.status, error: `HTTP ${res.status}` });
    }
    if (!contentType.toLowerCase().includes("html")) {
      return emptyPage(res.url, { status: res.status, error: `Not HTML: ${contentType || "unknown"}` });
    }
    const page = parseHtml(await res.text(), res.url, selector);
    page.status = res.status;
    return page;
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export class Crawler {
  constructor(scraper = new Scraper(), {
    maxDepth = 0, maxPages = 50, delay = 1000,
    sameHost = true, respectRobots = true, selector = null,
  } = {}) {
    this.scraper = scraper;
    this.options = { maxDepth, maxPages, delay, sameHost, respectRobots, selector };
    this.robots = new Map();
  }

  async allowed(url) {
    if (!this.options.respectRobots) return true;
    const { origin } = new URL(url);
    if (!this.robots.has(origin)) this.robots.set(origin, await this.#loadRobots(origin));
    return this.robots.get(origin)(url);
  }

  async #loadRobots(origin) {
    const robotsUrl = `${origin}/robots.txt`;
    try {
      const res = await this.scraper.get(robotsUrl);
      if (res.status === 401 || res.status === 403) return () => false;
      if (res.status >= 400) return () => true;
      const robots = robotsParser(robotsUrl, await res.text());
      return (url) => robots.isAllowed(url, this.scraper.userAgent) !== false;
    } catch {
      return () => true;
    }
  }

  /** Yield pages as they are scraped, breadth-first from `startUrl`. */
  async *crawl(startUrl) {
    const start = normalizeUrl(startUrl);
    if (!start) throw new Error(`Not an http(s) URL: ${startUrl}`);

    const startHost = new URL(start).host;
    const queue = [[start, 0]];
    const seen = new Set([start]);
    let fetched = 0;

    while (queue.length && fetched < this.options.maxPages) {
      const [url, depth] = queue.shift();
      if (!(await this.allowed(url))) {
        yield emptyPage(url, { error: "Blocked by robots.txt" });
        continue;
      }
      if (fetched && this.options.delay > 0) await sleep(this.options.delay);
      const page = await this.scraper.scrape(url, this.options.selector);
      fetched++;
      yield page;

      if (depth >= this.options.maxDepth) continue;
      for (const link of page.links) {
        if (seen.has(link)) continue;
        if (this.options.sameHost && new URL(link).host !== startHost) continue;
        seen.add(link);
        queue.push([link, depth + 1]);
      }
    }
  }
}
