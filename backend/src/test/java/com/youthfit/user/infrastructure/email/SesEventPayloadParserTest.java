package com.youthfit.user.infrastructure.email;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

class SesEventPayloadParserTest {

    private final SesEventPayloadParser parser = new SesEventPayloadParser(new ObjectMapper());

    @Test
    void parse_delivery_event() {
        String json = """
            {"eventType":"Delivery",
             "mail":{"messageId":"0000014a-f4d4-4f06-b2c4-1234abcd"},
             "delivery":{"timestamp":"2026-05-05T10:00:30.000Z","recipients":["u@ex.com"]}}
            """;
        SesEvent ev = parser.parse(json);
        assertThat(ev.eventType()).isEqualTo(SesEvent.Type.DELIVERY);
        assertThat(ev.messageId()).isEqualTo("0000014a-f4d4-4f06-b2c4-1234abcd");
    }

    @Test
    void parse_bounce_permanent() {
        String json = """
            {"eventType":"Bounce",
             "mail":{"messageId":"msg-2"},
             "bounce":{"bounceType":"Permanent","bounceSubType":"NoEmail",
                       "bouncedRecipients":[{"emailAddress":"u@ex.com",
                         "diagnosticCode":"5.1.1 user unknown"}]}}
            """;
        SesEvent ev = parser.parse(json);
        assertThat(ev.eventType()).isEqualTo(SesEvent.Type.BOUNCE);
        assertThat(ev.bounceType()).isEqualTo("Permanent");
        assertThat(ev.diagnosticReason()).contains("5.1.1");
    }

    @Test
    void parse_complaint_event() {
        String json = """
            {"eventType":"Complaint",
             "mail":{"messageId":"msg-3"},
             "complaint":{"complainedRecipients":[{"emailAddress":"u@ex.com"}]}}
            """;
        SesEvent ev = parser.parse(json);
        assertThat(ev.eventType()).isEqualTo(SesEvent.Type.COMPLAINT);
    }
}
