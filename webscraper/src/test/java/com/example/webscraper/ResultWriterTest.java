package com.example.webscraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;

class ResultWriterTest {

    private static final PageResult PAGE = new PageResult("https://example.com/", 0, 200,
            "Say \"hi\", world", "line1\nline2", List.of("H1"), List.of("https://example.com/a"),
            List.of(), List.of("x", "y"), null);

    @Test
    void escapesJsonStrings() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\"", ResultWriter.json("a\"b\\c\n\u0001"));
    }

    @Test
    void writesJson() throws Exception {
        StringWriter out = new StringWriter();
        ResultWriter.write(List.of(PAGE), ResultWriter.Format.JSON, out);
        assertEquals("""
                [
                  {
                    "url": "https://example.com/",
                    "depth": 0,
                    "status": 200,
                    "title": "Say \\"hi\\", world",
                    "description": "line1\\nline2",
                    "headings": ["H1"],
                    "links": ["https://example.com/a"],
                    "images": [],
                    "selected": ["x", "y"],
                    "error": null
                  }
                ]
                """, out.toString());
    }

    @Test
    void writesCsvWithQuoting() throws Exception {
        StringWriter out = new StringWriter();
        ResultWriter.write(List.of(PAGE), ResultWriter.Format.CSV, out);
        String[] lines = out.toString().split("\r\n");
        assertEquals("url,depth,status,title,description,headings,link_count,image_count,selected,error", lines[0]);
        assertEquals("https://example.com/,0,200,\"Say \"\"hi\"\", world\",\"line1\nline2\",H1,1,0,x | y,", lines[1]);
    }

    @Test
    void normalizesUrls() {
        assertEquals("https://example.com/", PageScraper.normalize("HTTPS://Example.COM"));
        assertEquals("http://example.com/a?b=1", PageScraper.normalize("http://example.com:80/a?b=1#frag"));
        assertEquals("http://example.com:8080/", PageScraper.normalize("http://example.com:8080"));
        assertNull(PageScraper.normalize("javascript:void(0)"));
        assertNull(PageScraper.normalize("mailto:a@b.c"));
    }
}
