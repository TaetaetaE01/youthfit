package com.youthfit.user.infrastructure.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SesEventPayloadParser {

    private final ObjectMapper mapper;

    public SesEvent parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String type = root.path("eventType").asText();
            String messageId = root.path("mail").path("messageId").asText();

            return switch (type) {
                case "Delivery" -> SesEvent.delivery(messageId);
                case "Bounce" -> {
                    JsonNode bounce = root.path("bounce");
                    String bounceType = bounce.path("bounceType").asText();
                    String diag = bounce.path("bouncedRecipients").path(0)
                        .path("diagnosticCode").asText("");
                    yield SesEvent.bounce(messageId, bounceType, diag);
                }
                case "Complaint" -> SesEvent.complaint(messageId);
                default -> throw new IllegalArgumentException("Unknown SES eventType: " + type);
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("SES event 파싱 실패", e);
        }
    }
}

record SesEvent(Type eventType, String messageId, String bounceType, String diagnosticReason) {
    enum Type { DELIVERY, BOUNCE, COMPLAINT }

    static SesEvent delivery(String id) { return new SesEvent(Type.DELIVERY, id, null, null); }
    static SesEvent bounce(String id, String type, String reason) {
        return new SesEvent(Type.BOUNCE, id, type, reason);
    }
    static SesEvent complaint(String id) { return new SesEvent(Type.COMPLAINT, id, null, null); }
}
