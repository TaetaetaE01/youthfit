package com.youthfit.user.infrastructure.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SnsMessageVerifier {

    private static final Pattern CERT_URL_PATTERN =
        Pattern.compile("^https://sns\\.[a-z0-9-]+\\.amazonaws\\.com(\\.cn)?/.+\\.pem$");

    private final RestClient restClient = RestClient.create();

    public void verify(SnsMessage message) {
        if (!"1".equals(message.signatureVersion())) {
            throw new SecurityException("지원하지 않는 SNS SignatureVersion: " + message.signatureVersion());
        }
        if (message.signingCertUrl() == null
            || !CERT_URL_PATTERN.matcher(message.signingCertUrl()).matches()) {
            throw new SecurityException("신뢰할 수 없는 SigningCertURL: " + message.signingCertUrl());
        }
        // 본격 X.509 서명 검증은 후속 운영 강화. 현재는 cert URL/version 표면 검증만.
        log.debug("SNS 메시지 표면 검증 통과 type={} topicArn={}",
                  message.type(), message.topicArn());
    }

    public void confirmSubscription(String subscribeUrl) {
        if (subscribeUrl == null) {
            throw new IllegalArgumentException("SubscribeURL 누락");
        }
        log.info("SNS subscription 확인 호출: {}", subscribeUrl);
        restClient.get().uri(URI.create(subscribeUrl)).retrieve().toBodilessEntity();
    }
}
