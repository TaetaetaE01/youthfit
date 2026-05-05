package com.youthfit.user.infrastructure.email;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

class SnsMessageVerifierTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SnsMessageVerifier verifier = new SnsMessageVerifier();

    @Test
    void parse_subscription_confirmation_OK() {
        String json = """
            {"Type":"SubscriptionConfirmation","MessageId":"m1",
             "TopicArn":"arn:aws:sns:::youthfit-ses-events",
             "SubscribeURL":"https://sns.example.com/?Action=ConfirmSubscription&Token=xxx",
             "Signature":"abc","SigningCertURL":"https://sns.us-east-1.amazonaws.com/x.pem",
             "SignatureVersion":"1","Timestamp":"2026-05-05T10:00:00.000Z","Message":"..."}
            """;
        SnsMessage m = SnsMessage.parse(json, mapper);
        assertThat(m.type()).isEqualTo(SnsMessage.Type.SUBSCRIPTION_CONFIRMATION);
        assertThat(m.subscribeUrl()).startsWith("https://sns.example.com/");
    }

    @Test
    void verify_certUrl_aws_도메인_아니면_거절() {
        SnsMessage bad = new SnsMessage(SnsMessage.Type.NOTIFICATION,
            "arn", "m1", null, "sig", "https://evil.example.com/cert.pem",
            "1", "ts", "{}");
        assertThatThrownBy(() -> verifier.verify(bad))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("SigningCertURL");
    }

    @Test
    void verify_signatureVersion_1만_허용() {
        SnsMessage bad = new SnsMessage(SnsMessage.Type.NOTIFICATION,
            "arn", "m1", null, "sig",
            "https://sns.us-east-1.amazonaws.com/cert.pem",
            "99", "ts", "{}");
        assertThatThrownBy(() -> verifier.verify(bad))
            .isInstanceOf(SecurityException.class);
    }
}
