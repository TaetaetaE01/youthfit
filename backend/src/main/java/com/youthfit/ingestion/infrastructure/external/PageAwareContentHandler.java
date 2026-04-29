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
 * <p>동작 / 가정:
 * <ul>
 *   <li>페이지 마커가 발생하면 새 페이지 시작 (현재 누적분을 PageText 로 flush)</li>
 *   <li>페이지 마커가 한 번도 발생 안 하면 (e.g. plain text / HWP) 단일
 *       {@code PageText(page=null, text=전체본문)} 로 종료</li>
 *   <li>첫 페이지 마커 이전에 등장하는 텍스트(헤더/메타가 본문으로 흘러들어온 경우)는
 *       페이지 누적에서 제외된다 — Tika 의 PDFParser 는 본문 텍스트를 모두 페이지 마커
 *       안에 emit 하므로 실제 PDF 케이스에선 데이터 손실이 없다.</li>
 *   <li>{@link #flushPage()} 가 빈 페이지(공백만 있는 페이지)는 결과에서 제외하므로
 *       {@code pages.size()} 와 실제 PDF 페이지 수가 차이 날 수 있다. 본 추출의 목적은
 *       LLM 입력에 들어갈 텍스트 수집이라 빈 페이지는 의미가 없어 의도적으로 drop.</li>
 * </ul>
 */
public class PageAwareContentHandler extends ContentHandlerDecorator {

    /**
     * {@link ContentHandlerDecorator#getContentHandler()} 가 Tika 2.9.x 에서 package-private
     * 이라 fallback 의 {@code body.toString()} 호출용 직접 참조 보유.
     */
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
        if ("div".equals(localName)) {
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
