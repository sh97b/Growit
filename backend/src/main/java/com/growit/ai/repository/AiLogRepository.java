package com.growit.ai.repository;

import com.growit.ai.entity.AiLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link AiLog} 조회. AI 기능이 아직 없어 현재는 어디서도 호출되지 않는다.
 */
public interface AiLogRepository extends JpaRepository<AiLog, Long> {
}
