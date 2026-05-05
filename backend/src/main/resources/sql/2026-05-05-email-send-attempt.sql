CREATE TABLE email_send_attempt (
    id                      BIGSERIAL PRIMARY KEY,
    notification_history_id BIGINT,
    recipient_email         VARCHAR(255) NOT NULL,
    recipient_user_id       BIGINT,
    email_type              VARCHAR(30) NOT NULL,
    subject                 VARCHAR(500) NOT NULL,
    input_payload           JSONB NOT NULL,
    ses_message_id          VARCHAR(255),
    status                  VARCHAR(20) NOT NULL,
    error_code              VARCHAR(100),
    error_message           VARCHAR(1000),
    bounce_type             VARCHAR(50),
    sent_at                 TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL
);

CREATE INDEX idx_email_attempt_message_id ON email_send_attempt(ses_message_id) WHERE ses_message_id IS NOT NULL;
CREATE INDEX idx_email_attempt_history_id ON email_send_attempt(notification_history_id);
CREATE INDEX idx_email_attempt_sent_at    ON email_send_attempt(sent_at DESC);
CREATE INDEX idx_email_attempt_status     ON email_send_attempt(status);
