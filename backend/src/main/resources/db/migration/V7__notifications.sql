CREATE TABLE notification_settings (
    id                       BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT      NOT NULL,
    enabled                  BOOLEAN     NOT NULL DEFAULT FALSE,
    time_of_day              VARCHAR(5)  NOT NULL DEFAULT '22:00',
    days_of_week             VARCHAR(7)  NOT NULL DEFAULT '1111111',
    notify_on_coach_arrival  BOOLEAN     NOT NULL DEFAULT TRUE,
    last_sent_date           DATE        NULL,
    created_at               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_settings_user_id UNIQUE (user_id),
    CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE push_subscriptions (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    endpoint    VARCHAR(500) NOT NULL,
    p256dh      VARCHAR(255) NOT NULL,
    auth        VARCHAR(255) NOT NULL,
    user_agent  VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_push_subscriptions_endpoint UNIQUE (endpoint(255)),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_push_subscriptions_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
