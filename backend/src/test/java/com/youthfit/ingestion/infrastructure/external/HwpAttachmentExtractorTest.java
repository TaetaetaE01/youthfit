package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class HwpAttachmentExtractorTest {

    private final HwpAttachmentExtractor sut = new HwpAttachmentExtractor();

    @Test
    void supports_는_HWP_관련_mediaType만_true() {
        assertThat(sut.supports("application/x-hwp")).isTrue();
        assertThat(sut.supports("application/haansofthwp")).isTrue();
        assertThat(sut.supports("application/vnd.hancom.hwp")).isTrue();
    }

    @Test
    void supports_는_PDF_등은_false() {
        assertThat(sut.supports("application/pdf")).isFalse();
        assertThat(sut.supports("text/plain")).isFalse();
        assertThat(sut.supports(null)).isFalse();
    }

    @Test
    void hwpx_zip_파일의_본문_텍스트를_문단단위로_추출한다() throws Exception {
        // youth.seoul 첨부(.hwpx)는 mediaType 이 application/x-hwp 로 들어오지만
        // 실제 바이트는 구형 hwp(OLE2)가 아니라 zip 기반 hwpx 다.
        byte[] hwpx = buildHwpx(
                "<hp:p><hp:run><hp:t>청년수당 신청 안내</hp:t></hp:run></hp:p>"
                        + "<hp:p><hp:run><hp:t>제출서류: </hp:t></hp:run>"
                        + "<hp:run><hp:t>주민등록등본</hp:t></hp:run></hp:p>");

        ExtractionResult result = sut.extract(new ByteArrayInputStream(hwpx), hwpx.length);

        assertThat(result).isInstanceOf(ExtractionResult.Success.class);
        String text = ((ExtractionResult.Success) result).text();
        assertThat(text).contains("청년수당 신청 안내");
        // 같은 문단의 분리된 run 은 한 줄로 이어붙는다
        assertThat(text).contains("제출서류: 주민등록등본");
    }

    /** Contents/section0.xml 하나만 담은 최소 hwpx(zip) 바이트를 만든다. */
    private byte[] buildHwpx(String sectionParagraphs) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            putEntry(zos, "mimetype", "application/hwp+zip");
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<hs:sec xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\""
                    + " xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\">"
                    + sectionParagraphs + "</hs:sec>";
            putEntry(zos, "Contents/section0.xml", xml);
        }
        return bos.toByteArray();
    }

    private void putEntry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
