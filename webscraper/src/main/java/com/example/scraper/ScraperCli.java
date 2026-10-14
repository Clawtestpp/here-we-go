package com.example.scraper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Command-line entry point. Run with {@code --help} for usage. */
public final class ScraperCli {

    private static final String USAGE = """
            Usage: java -jar webscraper.jar <url> [options]

            Fetches a web page and extracts its title, description, headings, links and images.
            With --depth > 0 it crawls linked pages breadth-first.

            Options:
              -d, --depth <n>        Link depth to crawl (default 0 = start page only)
              -m, --max-pages <n>    Maximum pages to fetch (default 50)
              -s, --selector <css>   Also collect elements matching this CSS selector
              -a, --attr <name>      With --selector, collect this attribute instead of text
              -f, --format <fmt>     Output format: text (default) or json
              -o, --output <file>    Write output to a file instead of stdout
                  --delay <ms>       Minimum delay between requests (default 500)
                  --timeout <ms>     Per-request timeout (default 10000)
                  --user-agent <ua>  User-Agent header to send
                  --all-hosts        Follow links to other hosts too
                  --ignore-robots    Do not consult robots.txt
              -h, --help             Show this help

            Examples:
              java -jar webscraper.jar https://example.com
              java -jar webscraper.jar https://example.com -d 1 -f json -o pages.json
              java -jar webscraper.jar https://news.ycombinator.com -s ".titleline > a" -a href
            """;

    private ScraperCli() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        ScraperConfig.Builder builder = ScraperConfig.builder();
        String url = null, format = "text", output = null;

        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h", "--help" -> { out.print(USAGE); return 0; }
                    case "-d", "--depth" -> builder.maxDepth(parseInt(arg, value(args, ++i, arg)));
                    case "-m", "--max-pages" -> builder.maxPages(parseInt(arg, value(args, ++i, arg)));
                    case "-s", "--selector" -> builder.selector(value(args, ++i, arg));
                    case "-a", "--attr" -> builder.attribute(value(args, ++i, arg));
                    case "-f", "--format" -> format = value(args, ++i, arg).toLowerCase();
                    case "-o", "--output" -> output = value(args, ++i, arg);
                    case "--delay" -> builder.delayMillis(parseInt(arg, value(args, ++i, arg)));
                    case "--timeout" -> builder.timeoutMillis(parseInt(arg, value(args, ++i, arg)));
                    case "--user-agent" -> builder.userAgent(value(args, ++i, arg));
                    case "--all-hosts" -> builder.sameHostOnly(false);
                    case "--ignore-robots" -> builder.respectRobots(false);
                    default -> {
                        if (arg.startsWith("-")) throw new IllegalArgumentException("Unknown option: " + arg);
                        if (url != null) throw new IllegalArgumentException("Only one URL may be given");
                        url = arg;
                    }
                }
            }
            if (url == null) throw new IllegalArgumentException("Missing URL");
            if (!format.equals("text") && !format.equals("json")) {
                throw new IllegalArgumentException("--format must be 'text' or 'json'");
            }
            ScraperConfig config = builder.build();

            Crawler crawler = new Crawler(config);
            int[] failures = {0};
            Writer writer = output == null
                    ? new OutputStreamWriter(out, StandardCharsets.UTF_8)
                    : Files.newBufferedWriter(Path.of(output), StandardCharsets.UTF_8);
            try {
                if (format.equals("json")) {
                    writeJson(crawler, url, writer, failures);
                } else {
                    crawler.crawl(url, page -> {
                        if (!page.isSuccess()) failures[0]++;
                        writeText(page, writer);
                    });
                }
                writer.flush();
            } finally {
                if (output != null) writer.close(); // never close stdout
            }
            if (output != null) err.println("Wrote results to " + output);
            return failures[0] > 0 ? 2 : 0;
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            err.println("Run with --help for usage.");
            return 1;
        } catch (IOException | UncheckedIOException | JsonIOException e) {
            err.println("I/O error: " + e.getMessage());
            return 1;
        }
    }

    /** Streams a JSON array so large crawls don't need to be held in memory. */
    private static void writeJson(Crawler crawler, String url, Writer writer, int[] failures) throws IOException {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        JsonWriter json = new JsonWriter(writer);
        json.setIndent("  ");
        json.beginArray();
        crawler.crawl(url, page -> {
            if (!page.isSuccess()) failures[0]++;
            gson.toJson(page, PageResult.class, json);
        });
        json.endArray();
        json.flush();
    }

    private static void writeText(PageResult page, Writer w) {
        try {
            w.write("=== " + page.url() + " (depth " + page.depth() + ")\n");
            if (!page.isSuccess()) {
                w.write("  ERROR: " + page.error() + "\n\n");
                w.flush();
                return;
            }
            if (!page.finalUrl().equals(page.url())) w.write("  Redirected to: " + page.finalUrl() + "\n");
            w.write("  Status:      " + page.statusCode() + "\n");
            w.write("  Title:       " + nullToDash(page.title()) + "\n");
            w.write("  Description: " + nullToDash(page.description()) + "\n");
            for (Map.Entry<String, List<String>> e : page.headings().entrySet()) {
                for (String h : e.getValue()) w.write("  " + e.getKey().toUpperCase() + ": " + h + "\n");
            }
            writeList(w, "Links", page.links());
            writeList(w, "Images", page.images());
            if (!page.selected().isEmpty()) writeList(w, "Selected", page.selected());
            w.write("\n");
            w.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeList(Writer w, String label, List<String> items) throws IOException {
        w.write("  " + label + " (" + items.size() + "):\n");
        for (String item : items) w.write("    - " + item + "\n");
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) throw new IllegalArgumentException(option + " requires a value");
        return args[i];
    }

    private static int parseInt(String option, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " expects a number, got '" + value + "'");
        }
    }

    private static String nullToDash(String s) {
        return s == null ? "-" : s;
    }
}
