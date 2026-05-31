package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentDownloader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;

@Component
public class AttachmentHttpClient implements AttachmentDownloader {

    private final RestClient restClient;

    public AttachmentHttpClient(
            @Value("${attachment.download.connect-timeout-seconds:10}") int connectTimeout,
            @Value("${attachment.download.read-timeout-seconds:60}") int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        this.restClient = RestClient.builder()
                .requestFactory((ClientHttpRequestFactory) factory)
                .build();
    }

    @Override
    public DownloadedFile download(String url, long maxBytes) {
        try {
            // 응답 Content-Type 을 파싱하지 않고 raw 바이트를 직접 읽는다.
            // 일부 공공 사이트(youth.seoul.go.kr)는 'application-download' 같은
            // 슬래시 없는 비표준 MIME 을 내려주는데, retrieve().body() 경로는
            // 이를 MediaType 으로 파싱하려다 InvalidMediaTypeException 으로 실패한다.
            byte[] body = restClient.get()
                    .uri(URI.create(url))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new DownloadException("http " + response.getStatusCode().value() + ": " + url, null);
                        }
                        return response.getBody().readAllBytes();
                    });
            if (body == null || body.length == 0) throw new DownloadException("empty body: " + url, null);
            if (body.length > maxBytes) throw new OversizedException("size=" + body.length + " > max=" + maxBytes);
            String contentType = guessContentType(url);
            return new DownloadedFile(new ByteArrayInputStream(body), body.length, contentType);
        } catch (OversizedException | DownloadException e) {
            throw e;
        } catch (Exception e) {
            throw new DownloadException("download failed: " + url, e);
        }
    }

    private String guessContentType(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".hwp")) return "application/x-hwp";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
}
