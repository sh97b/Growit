package com.growit.ai.service;

import com.growit.ai.repository.AiResultRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiDataResetService {

	private final AiResultRepository aiResultRepository;
	private final ActivityRecordRepository activityRecordRepository;

	public AiDataResetService(AiResultRepository aiResultRepository,
			ActivityRecordRepository activityRecordRepository) {
		this.aiResultRepository = aiResultRepository;
		this.activityRecordRepository = activityRecordRepository;
	}

	/** ai_logs는 한도 계산에 쓰이므로 지우지 않고, ai_results와 기록의 분석 결과만 되돌린다. */
	@Transactional
	public void resetMyData(Long userId) {
		aiResultRepository.deleteByUserId(userId);
		for (ActivityRecord record : activityRecordRepository.findByUserId(userId)) {
			record.resetAnalysisForDataDeletion();
		}
	}
}
