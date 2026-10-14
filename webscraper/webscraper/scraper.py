"""Fetch a single page and extract structured data from it."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from urllib.parse import urldefrag, urljoin, urlparse

import requests
from bs4 import BeautifulSoup

DEFAULT_USER_AGENT = "here-we-go-webscraper/1.0 (+https://github.com/clawtestpp/here-we-go)"


@dataclass
class PageData:
    url: str
    status: int | None = None
    title: str = ""
    description: str = ""
    headings: list[str] = field(default_factory=list)
    links: list[str] = field(default_factory=list)
    images: list[str] = field(default_factory=list)
    matches: list[str] = field(default_factory=list)
    error: str | None = None

    def to_dict(self) -> dict:
        return asdict(self)


def normalize_url(url: str) -> str | None:
    """Drop the #fragment; return None for anything that isn't http(s)."""
    url, _ = urldefrag(url.strip())
    if urlparse(url).scheme not in ("http", "https"):
        return None
    return url


def parse_html(html: str, base_url: str, selector: str | None = None) -> PageData:
    """Extract title, meta description, headings, links, images and optional CSS matches."""
    soup = BeautifulSoup(html, "html.parser")
    page = PageData(url=base_url)

    if soup.title and soup.title.string:
        page.title = soup.title.string.strip()

    meta = soup.find("meta", attrs={"name": "description"})
    if meta and meta.get("content"):
        page.description = meta["content"].strip()

    page.headings = [
        h.get_text(" ", strip=True)
        for h in soup.find_all(["h1", "h2", "h3"])
        if h.get_text(strip=True)
    ]

    seen: set[str] = set()
    for a in soup.find_all("a", href=True):
        link = normalize_url(urljoin(base_url, a["href"]))
        if link and link not in seen:
            seen.add(link)
            page.links.append(link)

    page.images = list(
        dict.fromkeys(urljoin(base_url, img["src"]) for img in soup.find_all("img", src=True))
    )

    if selector:
        page.matches = [el.get_text(" ", strip=True) for el in soup.select(selector)]

    return page


class Scraper:
    def __init__(
        self,
        user_agent: str = DEFAULT_USER_AGENT,
        timeout: float = 10.0,
        session: requests.Session | None = None,
    ):
        self.timeout = timeout
        self.session = session or requests.Session()
        self.session.headers["User-Agent"] = user_agent

    @property
    def user_agent(self) -> str:
        return self.session.headers["User-Agent"]

    def get(self, url: str) -> requests.Response:
        return self.session.get(url, timeout=self.timeout)

    def scrape(self, url: str, selector: str | None = None) -> PageData:
        """Fetch and parse one page. Network and HTTP problems are recorded in `error`."""
        try:
            resp = self.get(url)
        except requests.RequestException as exc:
            return PageData(url=url, error=f"{type(exc).__name__}: {exc}")

        content_type = resp.headers.get("Content-Type", "")
        if resp.status_code >= 400:
            return PageData(url=resp.url, status=resp.status_code, error=f"HTTP {resp.status_code}")
        if "html" not in content_type.lower():
            return PageData(
                url=resp.url, status=resp.status_code, error=f"Not HTML: {content_type or 'unknown'}"
            )

        page = parse_html(resp.text, resp.url, selector)
        page.status = resp.status_code
        return page
