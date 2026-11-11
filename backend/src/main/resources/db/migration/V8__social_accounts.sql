ALTER TABLE users MODIFY COLUMN email VARCHAR(255) NULL;
ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(255) NULL;

CREATE TABLE social_accounts (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    provider          VARCHAR(10)  NOT NULL,
    provider_user_id  VARCHAR(100) NOT NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_social_accounts_provider_provider_user_id UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_social_accounts_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
