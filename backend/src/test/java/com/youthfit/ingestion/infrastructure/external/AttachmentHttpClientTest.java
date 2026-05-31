package com.youthfit.ingestion.infrastructure.external;

import com.sun.net.httpserver.HttpServer;
import com.youthfit.ingestion.application.port.AttachmentDownloader.DownloadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentHttpClientTest {

    private static final long MAX_BYTES = 50L * 1024 * 1024;

    private HttpServer server;
    private final AttachmentHttpClient sut = new AttachmentHttpClient(5, 10);

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void 슬래시_없는_비표준_ContentType_응답도_바이트로_정상_다운로드한다() throws Exception {
        // 청년몽땅정보통(youth.seoul.go.kr) 은 첨부에 'application-download;charset=UTF-8' 라는
        // type/subtype 슬래시가 없는 비표준 MIME 을 내려준다. RestClient 의 body 파싱 경로는
        // 이 헤더를 MediaType 으로 파싱하려다 InvalidMediaTypeException 으로 죽는다.
        byte[] payload = "HWPX-BINARY-CONTENT".getBytes(StandardCharsets.UTF_8);
        startServer("application-download;charset=UTF-8", 200, payload);

        try (DownloadedFile file = sut.download(baseUrl() + "/atch/fileDown.do?cnncSn=1&ordr=1", MAX_BYTES)) {
            assertThat(file.stream().readAllBytes()).isEqualTo(payload);
            assertThat(file.sizeBytes()).isEqualTo(payload.length);
        }
    }

    @Test
    void 정상_octet_stream_응답도_그대로_다운로드한다() throws Exception {
        byte[] payload = "PDF-BYTES".getBytes(StandardCharsets.UTF_8);
        startServer("application/octet-stream", 200, payload);

        try (DownloadedFile file = sut.download(baseUrl() + "/file.pdf", MAX_BYTES)) {
            assertThat(file.stream().readAllBytes()).isEqualTo(payload);
        }
    }

    private void startServer(String contentType, int status, byte[] body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
