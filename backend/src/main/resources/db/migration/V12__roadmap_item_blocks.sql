-- 로드맵 항목에 붙이는 블록(메모·링크·타이머·해 보는 방법·검색·하위 체크). 항목이 지워지면 함께 지워진다.
-- kind는 MEMO/LINK/TIMER/AI_HOWTO/SEARCH/SUBLIST, 정렬 칸 이름은 키워드(position)를 피해 sort_order로 쓴다.
CREATE TABLE roadmap_item_blocks (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    item_id     BIGINT       NOT NULL,
    kind        VARCHAR(20)  NOT NULL,
    sort_order  INT          NOT NULL,
    payload     JSON         NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_roadmap_item_blocks_item FOREIGN KEY (item_id) REFERENCES roadmap_items (id) ON DELETE CASCADE,
    INDEX idx_roadmap_item_blocks_item_id_sort_order (item_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
