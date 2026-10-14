"""A small, polite web scraper built on requests and BeautifulSoup."""

from .crawler import CrawlOptions, Crawler
from .scraper import PageData, Scraper, parse_html

__all__ = ["CrawlOptions", "Crawler", "PageData", "Scraper", "parse_html"]
