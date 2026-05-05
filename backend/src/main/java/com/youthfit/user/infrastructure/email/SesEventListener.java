package com.youthfit.user.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequestMapping("/api/internal/notifications")
@RequiredArgsConstructor
public class SesEventListener {

    private final SnsMessageVerifier verifier;
    private final SesEventHandler handler;
    private final SesEventPayloadParser parser;
    private final ObjectMapper objectMapper;

    @PostMapping("/ses-event")
    public ResponseEntity<Void> handle(@RequestBody String rawJson) {
        SnsMessage message = SnsMessage.parse(rawJson, objectMapper);
        verifier.verify(message);

        switch (message.type()) {
            case SUBSCRIPTION_CONFIRMATION -> {
                log.info("SNS SubscriptionConfirmation 수신 topicArn={}", message.topicArn());
                verifier.confirmSubscription(message.subscribeUrl());
            }
            case NOTIFICATION -> {
                SesEvent event = parser.parse(message.message());
                handler.handle(event);
            }
            case UNSUBSCRIBE_CONFIRMATION ->
                log.info("SNS UnsubscribeConfirmation 수신 topicArn={}", message.topicArn());
        }
        return ResponseEntity.ok().build();
    }
}
