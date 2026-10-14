"""Command-line interface: python -m webscraper URL [options]"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections.abc import Iterable
from typing import TextIO

from .crawler import CrawlOptions, Crawler
from .scraper import DEFAULT_USER_AGENT, PageData, Scraper

CSV_FIELDS = ["url", "status", "title", "description", "headings", "links", "images", "matches", "error"]


def write_json(pages: Iterable[PageData], out: TextIO) -> None:
    json.dump([p.to_dict() for p in pages], out, indent=2, ensure_ascii=False)
    out.write("\n")


def write_jsonl(pages: Iterable[PageData], out: TextIO) -> None:
    for p in pages:
        out.write(json.dumps(p.to_dict(), ensure_ascii=False) + "\n")
        out.flush()


def write_csv(pages: Iterable[PageData], out: TextIO) -> None:
    writer = csv.DictWriter(out, fieldnames=CSV_FIELDS)
    writer.writeheader()
    for p in pages:
        row = p.to_dict()
        for key in ("headings", "links", "images", "matches"):
            row[key] = " | ".join(row[key])
        writer.writerow(row)


WRITERS = {"json": write_json, "jsonl": write_jsonl, "csv": write_csv}


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="webscraper",
        description="Scrape a web page (or crawl a site) and export titles, headings, links and more.",
    )
    p.add_argument("url", help="start URL (http or https)")
    p.add_argument("-d", "--depth", type=int, default=0, help="link depth to follow (default: 0, start page only)")
    p.add_argument("-n", "--max-pages", type=int, default=50, help="maximum pages to fetch (default: 50)")
    p.add_argument("-s", "--selector", help="CSS selector whose matching text to extract, e.g. 'h2.title'")
    p.add_argument("-f", "--format", choices=WRITERS, default="json", help="output format (default: json)")
    p.add_argument("-o", "--output", help="write to this file instead of stdout")
    p.add_argument("--delay", type=float, default=1.0, help="seconds to wait between requests (default: 1)")
    p.add_argument("--timeout", type=float, default=10.0, help="request timeout in seconds (default: 10)")
    p.add_argument("--user-agent", default=DEFAULT_USER_AGENT, help="User-Agent header to send")
    p.add_argument("--all-hosts", action="store_true", help="follow links to other hosts too")
    p.add_argument("--ignore-robots", action="store_true", help="don't check robots.txt")
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    crawler = Crawler(
        Scraper(user_agent=args.user_agent, timeout=args.timeout),
        CrawlOptions(
            max_depth=args.depth,
            max_pages=args.max_pages,
            delay=args.delay,
            same_host=not args.all_hosts,
            respect_robots=not args.ignore_robots,
            selector=args.selector,
        ),
    )

    def progress(pages: Iterable[PageData]) -> Iterable[PageData]:
        for i, page in enumerate(pages, 1):
            status = page.error or page.status
            print(f"[{i}] {page.url} -> {status}", file=sys.stderr)
            yield page

    try:
        pages = progress(crawler.crawl(args.url))
        if args.output:
            with open(args.output, "w", encoding="utf-8", newline="") as out:
                WRITERS[args.format](pages, out)
        else:
            WRITERS[args.format](pages, sys.stdout)
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 130
    return 0
