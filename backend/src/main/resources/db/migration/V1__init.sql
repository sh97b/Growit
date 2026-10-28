CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hobbies (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    goal       VARCHAR(200) NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_hobbies_user_id_name UNIQUE (user_id, name),
    CONSTRAINT fk_hobbies_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE records (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    hobby_id      BIGINT      NOT NULL,
    record_date   DATE        NOT NULL,
    content       TEXT        NOT NULL,
    minutes       INT         NULL,
    difficulty    TEXT        NULL,
    feeling       VARCHAR(20) NULL,
    is_milestone  BOOLEAN     NOT NULL DEFAULT FALSE,
    ai_analysis   JSON        NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_records_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_records_hobby FOREIGN KEY (hobby_id) REFERENCES hobbies (id),
    INDEX idx_records_user_id_record_date (user_id, record_date),
    INDEX idx_records_hobby_id_record_date (hobby_id, record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_results (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    hobby_id    BIGINT      NULL,
    result_type VARCHAR(20) NOT NULL,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    result      JSON        NOT NULL,
    record_ids  JSON        NOT NULL,
    cache_key   CHAR(64)    NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_results_user_id_cache_key UNIQUE (user_id, cache_key),
    CONSTRAINT fk_ai_results_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_results_hobby FOREIGN KEY (hobby_id) REFERENCES hobbies (id),
    INDEX idx_ai_results_user_id_result_type_created_at (user_id, result_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    result_type   VARCHAR(20) NOT NULL,
    state         VARCHAR(10) NOT NULL,
    response_ms   INT         NULL,
    token_count   INT         NULL,
    error_message VARCHAR(500) NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_ai_logs_user_id_created_at (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
