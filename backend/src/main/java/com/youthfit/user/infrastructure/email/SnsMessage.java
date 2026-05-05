package com.youthfit.user.infrastructure.email;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record SnsMessage(
        Type type,
        String topicArn,
        String messageId,
        String subscribeUrl,
        String signature,
        String signingCertUrl,
        String signatureVersion,
        String timestamp,
        String message
) {
    public enum Type { SUBSCRIPTION_CONFIRMATION, NOTIFICATION, UNSUBSCRIBE_CONFIRMATION }

    public static SnsMessage parse(String rawJson, ObjectMapper mapper) {
        try {
            JsonNode node = mapper.readTree(rawJson);
            String typeStr = node.get("Type").asText();
            Type type = switch (typeStr) {
                case "SubscriptionConfirmation" -> Type.SUBSCRIPTION_CONFIRMATION;
                case "Notification" -> Type.NOTIFICATION;
                case "UnsubscribeConfirmation" -> Type.UNSUBSCRIBE_CONFIRMATION;
                default -> throw new IllegalArgumentException("Unknown SNS Type: " + typeStr);
            };
            return new SnsMessage(
                type,
                text(node, "TopicArn"),
                text(node, "MessageId"),
                text(node, "SubscribeURL"),
                text(node, "Signature"),
                text(node, "SigningCertURL"),
                text(node, "SignatureVersion"),
                text(node, "Timestamp"),
                text(node, "Message"));
        } catch (Exception e) {
            throw new IllegalArgumentException("SNS 메시지 파싱 실패", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
