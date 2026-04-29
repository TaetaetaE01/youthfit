package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.domain.model.PageText;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

/**
 * Tika SAX 핸들러. PDFParser 가 발생시키는 페이지 마커 (&lt;div class="page"&gt;) 를 인지해
 * 페이지별 텍스트를 누적한다.
 *
 * - 페이지 마커가 발생하면 새 페이지 시작 (현재 누적분을 PageText 로 flush)
 * - 페이지 마커가 한 번도 발생 안 하면 (e.g. plain text) 단일 PageText(page=null, text=...) 로 종료
 */
public class PageAwareContentHandler extends ContentHandlerDecorator {

    private final BodyContentHandler body;
    private final List<PageText> pages = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private int pageNumber = 0;
    private boolean sawAnyPageMarker = false;

    public PageAwareContentHandler() {
        this(new BodyContentHandler(-1));
    }

    private PageAwareContentHandler(BodyContentHandler body) {
        super(body);
        this.body = body;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        super.startElement(uri, localName, qName, atts);
        if ("div".equalsIgnoreCase(localName)) {
            String cls = atts.getValue("class");
            if ("page".equals(cls)) {
                if (sawAnyPageMarker) {
                    flushPage();
                }
                sawAnyPageMarker = true;
                pageNumber++;
                current.setLength(0);
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);
        if (sawAnyPageMarker) {
            current.append(ch, start, length);
        }
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        if (sawAnyPageMarker) {
            flushPage();
        } else {
            pages.add(new PageText(null, body.toString()));
        }
    }

    private void flushPage() {
        String text = current.toString().trim();
        if (!text.isEmpty()) {
            pages.add(new PageText(pageNumber, text));
        }
    }

    public List<PageText> getPages() {
        return List.copyOf(pages);
    }
}
