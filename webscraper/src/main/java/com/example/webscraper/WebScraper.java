package com.example.webscraper;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.jsoup.select.QueryParser;
import org.jsoup.select.Selector;

/** Command-line entry point. Run with {@code --help} for usage. */
public final class WebScraper {

    static final String DEFAULT_USER_AGENT = "WebScraper/1.0 (+https://github.com/clawtestpp/here-we-go)";

    private static final String USAGE = """
            Usage: java -jar webscraper.jar <url> [options]

            Scrapes a page (and optionally crawls the pages it links to), extracting the
            title, meta description, h1-h3 headings, links and images.

            Options:
              -d, --depth N          how many links deep to crawl (default 0: only <url>)
              -m, --max-pages N      stop after N pages (default 50)
              -s, --select CSS       also capture the text of elements matching CSS,
                                     e.g. "article h2" or ".price"
              -f, --format FMT       json or csv (default: from --output extension, else json)
              -o, --output FILE      write results to FILE instead of stdout
                  --delay MS         pause between requests in ms (default 1000)
                  --timeout MS       per-request timeout in ms (default 10000)
                  --user-agent UA    User-Agent header to send
                  --all-hosts        follow links to other hosts too
                  --ignore-robots    do not check robots.txt
              -q, --quiet            no progress output on stderr
              -h, --help             show this help
            """;

    private WebScraper() { }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        Args parsed;
        try {
            parsed = Args.parse(args);
        } catch (IllegalArgumentException e) {
            stderr.println("Error: " + e.getMessage());
            stderr.println();
            stderr.print(USAGE);
            return 2;
        }
        if (parsed.help) {
            stdout.print(USAGE);
            return 0;
        }

        PageScraper scraper = new PageScraper(parsed.userAgent, parsed.timeoutMillis, parsed.selector);
        Crawler crawler = new Crawler(new Crawler.Options(
                parsed.depth, parsed.maxPages, parsed.delayMillis,
                !parsed.allHosts, !parsed.ignoreRobots, parsed.userAgent, parsed.timeoutMillis), scraper);

        List<PageResult> results;
        try {
            results = crawler.crawl(parsed.url, page -> {
                if (!parsed.quiet) {
                    String outcome = page.ok() ? page.status() + " " + page.title() : "FAILED " + page.error();
                    stderr.printf("[%d] %s -> %s%n", page.depth(), page.url(), outcome);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stderr.println("Interrupted.");
            return 130;
        }

        try {
            if (parsed.output == null) {
                Writer out = new OutputStreamWriter(stdout, StandardCharsets.UTF_8);
                ResultWriter.write(results, parsed.format, out);
            } else {
                try (Writer out = Files.newBufferedWriter(parsed.output, StandardCharsets.UTF_8)) {
                    ResultWriter.write(results, parsed.format, out);
                }
                if (!parsed.quiet) {
                    stderr.printf("Wrote %d page(s) to %s%n", results.size(), parsed.output);
                }
            }
        } catch (IOException e) {
            stderr.println("Error writing results: " + e.getMessage());
            return 1;
        }

        boolean anyOk = results.stream().anyMatch(PageResult::ok);
        return anyOk ? 0 : 1;
    }

    /** Parsed command-line options. */
    static final class Args {
        String url;
        int depth = 0;
        int maxPages = 50;
        long delayMillis = 1000;
        int timeoutMillis = 10_000;
        String selector;
        ResultWriter.Format format;
        Path output;
        String userAgent = DEFAULT_USER_AGENT;
        boolean allHosts;
        boolean ignoreRobots;
        boolean quiet;
        boolean help;

        static Args parse(String[] argv) {
            Args a = new Args();
            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                switch (arg) {
                    case "-h", "--help" -> a.help = true;
                    case "-q", "--quiet" -> a.quiet = true;
                    case "--all-hosts" -> a.allHosts = true;
                    case "--ignore-robots" -> a.ignoreRobots = true;
                    case "-d", "--depth" -> a.depth = nonNegativeInt(arg, value(argv, ++i, arg));
                    case "-m", "--max-pages" -> a.maxPages = positiveInt(arg, value(argv, ++i, arg));
                    case "--delay" -> a.delayMillis = nonNegativeInt(arg, value(argv, ++i, arg));
                    case "--timeout" -> a.timeoutMillis = positiveInt(arg, value(argv, ++i, arg));
                    case "--user-agent" -> a.userAgent = value(argv, ++i, arg);
                    case "-s", "--select" -> a.selector = value(argv, ++i, arg);
                    case "-o", "--output" -> a.output = Path.of(value(argv, ++i, arg));
                    case "-f", "--format" -> a.format = format(value(argv, ++i, arg));
                    default -> {
                        if (arg.startsWith("-")) {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                        if (a.url != null) {
                            throw new IllegalArgumentException("Only one URL may be given");
                        }
                        a.url = arg;
                    }
                }
            }
            if (a.help) {
                return a;
            }
            if (a.url == null) {
                throw new IllegalArgumentException("Missing <url>");
            }
            if (!a.url.contains("://")) {
                a.url = "https://" + a.url;
            }
            if (PageScraper.normalize(a.url) == null) {
                throw new IllegalArgumentException("Not an http(s) URL: " + a.url);
            }
            if (a.selector != null) {
                try {
                    QueryParser.parse(a.selector);
                } catch (Selector.SelectorParseException e) {
                    throw new IllegalArgumentException("Invalid CSS selector: " + a.selector);
                }
            }
            if (a.format == null) {
                boolean csvFile = a.output != null
                        && a.output.toString().toLowerCase(Locale.ROOT).endsWith(".csv");
                a.format = csvFile ? ResultWriter.Format.CSV : ResultWriter.Format.JSON;
            }
            return a;
        }

        private static String value(String[] argv, int i, String option) {
            if (i >= argv.length) {
                throw new IllegalArgumentException(option + " needs a value");
            }
            return argv[i];
        }

        private static int nonNegativeInt(String option, String value) {
            int n = integer(option, value);
            if (n < 0) {
                throw new IllegalArgumentException(option + " must be 0 or more");
            }
            return n;
        }

        private static int positiveInt(String option, String value) {
            int n = integer(option, value);
            if (n < 1) {
                throw new IllegalArgumentException(option + " must be 1 or more");
            }
            return n;
        }

        private static int integer(String option, String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(option + " expects a number, got: " + value);
            }
        }

        private static ResultWriter.Format format(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "json" -> ResultWriter.Format.JSON;
                case "csv" -> ResultWriter.Format.CSV;
                default -> throw new IllegalArgumentException("--format must be json or csv");
            };
        }
    }
}
