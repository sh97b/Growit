CREATE TABLE dot_goals (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    title            VARCHAR(50)  NOT NULL,
    source_type      VARCHAR(10)  NOT NULL,
    preset_key       VARCHAR(30)  NULL,
    goal_days        INT          NOT NULL,
    width            INT          NOT NULL,
    height           INT          NOT NULL,
    grid_json        JSON         NOT NULL,
    palette_json     JSON         NOT NULL,
    started_on       DATE         NOT NULL,
    state            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    completed_on     DATE         NULL,
    completion_seen  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dot_goals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_dot_goals_goal_days CHECK (goal_days BETWEEN 14 AND 200),
    INDEX idx_dot_goals_user_id_state (user_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dot_progress (
    goal_id      BIGINT NOT NULL,
    record_date  DATE   NOT NULL,
    PRIMARY KEY (goal_id, record_date),
    CONSTRAINT fk_dot_progress_goal FOREIGN KEY (goal_id) REFERENCES dot_goals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
