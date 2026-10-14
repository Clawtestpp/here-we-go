package com.example.webscraper;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** Serialises scrape results as JSON or CSV without pulling in extra libraries. */
public final class ResultWriter {

    public enum Format { JSON, CSV }

    private ResultWriter() { }

    public static void write(List<PageResult> results, Format format, Writer out) throws IOException {
        switch (format) {
            case JSON -> writeJson(results, out);
            case CSV -> writeCsv(results, out);
        }
        out.flush();
    }

    // ---- JSON ---------------------------------------------------------------------------

    static void writeJson(List<PageResult> results, Writer out) throws IOException {
        out.write("[\n");
        for (int i = 0; i < results.size(); i++) {
            PageResult r = results.get(i);
            out.write("  {\n");
            field(out, "url", json(r.url()), true);
            field(out, "depth", Integer.toString(r.depth()), true);
            field(out, "status", Integer.toString(r.status()), true);
            field(out, "title", json(r.title()), true);
            field(out, "description", json(r.description()), true);
            field(out, "headings", json(r.headings()), true);
            field(out, "links", json(r.links()), true);
            field(out, "images", json(r.images()), true);
            field(out, "selected", json(r.selected()), true);
            field(out, "error", r.error() == null ? "null" : json(r.error()), false);
            out.write(i < results.size() - 1 ? "  },\n" : "  }\n");
        }
        out.write("]\n");
    }

    private static void field(Writer out, String name, String value, boolean comma) throws IOException {
        out.write("    \"" + name + "\": " + value + (comma ? ",\n" : "\n"));
    }

    private static String json(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(json(values.get(i)));
        }
        return sb.append(']').toString();
    }

    static String json(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ---- CSV ----------------------------------------------------------------------------

    static void writeCsv(List<PageResult> results, Writer out) throws IOException {
        out.write("url,depth,status,title,description,headings,link_count,image_count,selected,error\r\n");
        for (PageResult r : results) {
            out.write(String.join(",",
                    csv(r.url()),
                    Integer.toString(r.depth()),
                    Integer.toString(r.status()),
                    csv(r.title()),
                    csv(r.description()),
                    csv(String.join(" | ", r.headings())),
                    Integer.toString(r.links().size()),
                    Integer.toString(r.images().size()),
                    csv(String.join(" | ", r.selected())),
                    csv(r.error() == null ? "" : r.error())));
            out.write("\r\n");
        }
    }

    static String csv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
