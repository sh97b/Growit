CREATE TABLE custom_feelings (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    label       VARCHAR(8)  NOT NULL,
    direction   VARCHAR(10) NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_custom_feelings_user_id_label UNIQUE (user_id, label),
    CONSTRAINT fk_custom_feelings_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_custom_feelings_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 직접 추가한 감정을 쓴 기록은 라벨과 방향을 기록에 그대로 복사해 둔다. 감정 목록에서 지워도 기록에는 남는다.
-- 기본 4개 감정을 쓴 기록은 기존대로 feeling 칸만 쓰고 이 두 칸은 NULL이다.
ALTER TABLE records
    ADD COLUMN feeling_label     VARCHAR(8)  NULL,
    ADD COLUMN feeling_direction VARCHAR(10) NULL;
