import assert from "node:assert/strict";
import { createServer } from "node:http";
import { after, before, describe, test } from "node:test";
import { toCsvRow } from "../src/cli.js";
import { Crawler, Scraper, parseHtml } from "../src/scraper.js";

const PAGES = {
  "/": `<html><head><title> Home </title>
    <meta name="description" content="The home page"></head>
    <body><h1>Welcome</h1><h2 class="item">First</h2><h2 class="item">Second</h2>
    <a href="/a#top">A</a><a href="/b">B</a><a href="/private/x">P</a>
    <a href="https://elsewhere.example/">Ext</a><a href="mailto:x@y.z">Mail</a>
    <img src="/logo.png"></body></html>`,
  "/a": '<html><head><title>A</title></head><body><a href="/c">C</a></body></html>',
  "/b": "<html><head><title>B</title></head><body></body></html>",
  "/c": "<html><head><title>C</title></head><body></body></html>",
  "/private/x": "<html><head><title>Secret</title></head></html>",
};
const ROBOTS = "User-agent: *\nDisallow: /private/\n";

describe("parseHtml", () => {
  test("extracts fields", () => {
    const page = parseHtml(PAGES["/"], "http://site.test/", "h2.item");
    assert.equal(page.title, "Home");
    assert.equal(page.description, "The home page");
    assert.deepEqual(page.headings, ["Welcome", "First", "Second"]);
    assert.deepEqual(page.matches, ["First", "Second"]);
    assert.deepEqual(page.images, ["http://site.test/logo.png"]);
    // fragments stripped, relative links resolved, mailto dropped
    assert.deepEqual(page.links, [
      "http://site.test/a",
      "http://site.test/b",
      "http://site.test/private/x",
      "https://elsewhere.example/",
    ]);
  });

  test("csv row quotes and joins", () => {
    const row = toCsvRow({ ...parseHtml(PAGES["/"], "http://site.test/"), title: 'Say "hi", ok' });
    assert.match(row, /^http:\/\/site\.test\/,,"Say ""hi"", ok",/);
    assert.match(row, /Welcome \| First \| Second/);
  });
});

describe("crawler", () => {
  let server, base;

  before(async () => {
    server = createServer((req, res) => {
      if (req.url === "/robots.txt") {
        res.writeHead(200, { "Content-Type": "text/plain" }).end(ROBOTS);
      } else if (PAGES[req.url]) {
        res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" }).end(PAGES[req.url]);
      } else {
        res.writeHead(404, { "Content-Type": "text/plain" }).end("nope");
      }
    });
    await new Promise((r) => server.listen(0, "127.0.0.1", r));
    base = `http://127.0.0.1:${server.address().port}`;
  });
  after(() => server.close());

  const crawl = async (opts) => {
    const pages = [];
    for await (const p of new Crawler(new Scraper(), { delay: 0, ...opts }).crawl(base + "/")) pages.push(p);
    return pages;
  };

  test("depth 0 fetches only the start page", async () => {
    assert.deepEqual((await crawl({ maxDepth: 0 })).map((p) => p.title), ["Home"]);
  });

  test("respects depth, host and robots.txt", async () => {
    const pages = Object.fromEntries((await crawl({ maxDepth: 2 })).map((p) => [p.url.slice(base.length), p]));
    assert.deepEqual(new Set(Object.keys(pages)), new Set(["/", "/a", "/b", "/private/x", "/c"]));
    assert.equal(pages["/private/x"].error, "Blocked by robots.txt");
    assert.equal(pages["/c"].title, "C");
  });

  test("ignoreRobots and maxPages", async () => {
    const pages = await crawl({ maxDepth: 2, respectRobots: false, maxPages: 4 });
    assert.equal(pages.length, 4);
    assert.ok(pages.some((p) => p.title === "Secret"));
  });

  test("records HTTP errors", async () => {
    const page = await new Scraper().scrape(base + "/missing");
    assert.equal(page.status, 404);
    assert.equal(page.error, "HTTP 404");
  });
});
