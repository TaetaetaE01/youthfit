package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.domain.model.PageText;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageAwareContentHandlerTest {

    @Test
    void givenSamplePdf_whenParse_thenSplitsByPage() throws Exception {
        InputStream pdf = getClass().getResourceAsStream("/extractor/sample-3-pages.pdf");
        assertThat(pdf).isNotNull();

        PageAwareContentHandler handler = new PageAwareContentHandler();
        new AutoDetectParser().parse(pdf, handler, new Metadata(), new ParseContext());
        List<PageText> pages = handler.getPages();

        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).page()).isEqualTo(1);
        assertThat(pages.get(0).text()).isNotBlank();
        assertThat(pages.get(2).page()).isEqualTo(3);
    }

    @Test
    void givenPlainText_whenParse_thenSinglePageNullPage() throws Exception {
        InputStream txt = new ByteArrayInputStream("hello world".getBytes());

        PageAwareContentHandler handler = new PageAwareContentHandler();
        new AutoDetectParser().parse(txt, handler, new Metadata(), new ParseContext());
        List<PageText> pages = handler.getPages();

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).page()).isNull();
        assertThat(pages.get(0).text()).contains("hello world");
    }
}
