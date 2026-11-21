CREATE TABLE roadmaps (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    hobby_id         BIGINT       NOT NULL,
    goal_text        VARCHAR(200) NOT NULL,
    target_date      DATE         NULL,
    state            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    prompt_version   INT          NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- ACTIVE일 때만 hobby_id를 갖는 계산 칸. 유니크 인덱스(NULL은 여러 개 허용)로 분야당 ACTIVE 1개를 DB가 보장한다.
    active_hobby_id  BIGINT       GENERATED ALWAYS AS (CASE WHEN state = 'ACTIVE' THEN hobby_id END) STORED,
    CONSTRAINT fk_roadmaps_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_roadmaps_hobby FOREIGN KEY (hobby_id) REFERENCES hobbies (id),
    CONSTRAINT uk_roadmaps_active_hobby_id UNIQUE (active_hobby_id),
    INDEX idx_roadmaps_user_id_hobby_id (user_id, hobby_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 정렬 칸 이름은 MySQL 키워드(position)를 피해 sort_order로 쓴다.
CREATE TABLE roadmap_steps (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    roadmap_id  BIGINT       NOT NULL,
    sort_order  INT          NOT NULL,
    title       VARCHAR(40)  NOT NULL,
    period_hint VARCHAR(20)  NULL,
    CONSTRAINT fk_roadmap_steps_roadmap FOREIGN KEY (roadmap_id) REFERENCES roadmaps (id),
    INDEX idx_roadmap_steps_roadmap_id_sort_order (roadmap_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- text·source는 MySQL 키워드라 content·origin으로 쓴다.
CREATE TABLE roadmap_items (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    step_id     BIGINT       NOT NULL,
    sort_order  INT          NOT NULL,
    content     VARCHAR(60)  NOT NULL,
    done        BOOLEAN      NOT NULL DEFAULT FALSE,
    done_at     DATETIME     NULL,
    origin      VARCHAR(4)   NOT NULL,
    CONSTRAINT fk_roadmap_items_step FOREIGN KEY (step_id) REFERENCES roadmap_steps (id),
    INDEX idx_roadmap_items_step_id_sort_order (step_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
