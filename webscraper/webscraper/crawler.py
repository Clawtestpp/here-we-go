"""Breadth-first crawler that is polite by default (robots.txt, delay, same host)."""

from __future__ import annotations

import time
from collections import deque
from collections.abc import Iterator
from dataclasses import dataclass
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser

import requests

from .scraper import PageData, Scraper, normalize_url


@dataclass
class CrawlOptions:
    max_depth: int = 0  # 0 = only the start page
    max_pages: int = 50
    delay: float = 1.0  # seconds between requests
    same_host: bool = True
    respect_robots: bool = True
    selector: str | None = None


class Crawler:
    def __init__(self, scraper: Scraper, options: CrawlOptions | None = None):
        self.scraper = scraper
        self.options = options or CrawlOptions()
        self._robots: dict[str, RobotFileParser] = {}

    def allowed(self, url: str) -> bool:
        if not self.options.respect_robots:
            return True
        parts = urlparse(url)
        origin = f"{parts.scheme}://{parts.netloc}"
        if origin not in self._robots:
            self._robots[origin] = self._load_robots(origin)
        return self._robots[origin].can_fetch(self.scraper.user_agent, url)

    def _load_robots(self, origin: str) -> RobotFileParser:
        rp = RobotFileParser(origin + "/robots.txt")
        try:
            resp = self.scraper.get(rp.url)
        except requests.RequestException:
            rp.allow_all = True
            return rp
        # Same conventions as urllib.robotparser.read()
        if resp.status_code in (401, 403):
            rp.disallow_all = True
        elif resp.status_code >= 400:
            rp.allow_all = True
        else:
            rp.parse(resp.text.splitlines())
        return rp

    def crawl(self, start_url: str) -> Iterator[PageData]:
        """Yield pages as they are scraped, breadth-first from `start_url`."""
        start = normalize_url(start_url)
        if start is None:
            raise ValueError(f"Not an http(s) URL: {start_url}")

        start_host = urlparse(start).netloc
        queue: deque[tuple[str, int]] = deque([(start, 0)])
        seen = {start}
        fetched = 0

        while queue and fetched < self.options.max_pages:
            url, depth = queue.popleft()
            if not self.allowed(url):
                yield PageData(url=url, error="Blocked by robots.txt")
                continue

            if fetched and self.options.delay > 0:
                time.sleep(self.options.delay)
            page = self.scraper.scrape(url, self.options.selector)
            fetched += 1
            yield page

            if depth >= self.options.max_depth:
                continue
            for link in page.links:
                if link in seen:
                    continue
                if self.options.same_host and urlparse(link).netloc != start_host:
                    continue
                seen.add(link)
                queue.append((link, depth + 1))
