package com.growit.record.event;

import com.growit.record.service.RecordAnalysisService;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 뒤에만 실행되도록 {@code AFTER_COMMIT}을 쓴다(롤백된 저장에 대해 분석이 돌지 않게). {@code @Async} 대신
 * 전용 실행기에 직접 제출하는 이유는, {@code @Async}가 대기열이 찬 경우 던지는 예외가 커밋 콜백 호출 체인을 타고
 * 올라가 트랜잭션 커밋 자체를 실패시킬 위험이 있어서다(기록 저장은 AI와 무관해야 한다는 요구와 충돌). 그래서 여기서
 * 직접 {@link RejectedExecutionException}을 잡아 해당 기록만 FAILED로 남긴다.
 */
@Component
public class RecordAnalysisEventListener {

	private final RecordAnalysisService recordAnalysisService;
	private final ThreadPoolTaskExecutor aiAnalysisExecutor;

	public RecordAnalysisEventListener(RecordAnalysisService recordAnalysisService,
			ThreadPoolTaskExecutor aiAnalysisExecutor) {
		this.recordAnalysisService = recordAnalysisService;
		this.aiAnalysisExecutor = aiAnalysisExecutor;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRecordSaved(RecordSavedEvent event) {
		try {
			aiAnalysisExecutor.execute(() -> recordAnalysisService.analyze(event.recordId(), event.userId()));
		} catch (RejectedExecutionException e) {
			recordAnalysisService.markQueueRejected(event.recordId(), event.userId());
		}
	}
}
