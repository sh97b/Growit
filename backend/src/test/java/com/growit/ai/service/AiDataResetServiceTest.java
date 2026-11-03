package com.growit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.growit.ai.entity.AiResult;
import com.growit.ai.repository.AiResultRepository;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.AiStatus;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AiDataResetServiceTest {

	@Autowired
	private AiResultRepository aiResultRepository;

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	private AiDataResetService service() {
		return new AiDataResetService(aiResultRepository, activityRecordRepository);
	}

	@Test
	void resetMyData_deletesAiResultsAndResetsDoneRecordsOnly() {
		Long hobbyId = hobbyRepository.save(new Hobby(1L, "기타", null)).getId();
		aiResultRepository.save(new AiResult(1L, hobbyId, "WEEKLY", LocalDate.now(), LocalDate.now(),
				"{}", "[]", "a".repeat(64)));

		ActivityRecord done = new ActivityRecord(1L, hobbyId, LocalDate.now(), "연습", null, null, Feeling.PROUD, false);
		done.markAnalysisDone("{\"title\":\"제목\"}");
		activityRecordRepository.save(done);

		ActivityRecord skipped = new ActivityRecord(1L, hobbyId, LocalDate.now(), "연습2", null, null, null, false);
		skipped.markAnalysisSkipped();
		activityRecordRepository.save(skipped);

		service().resetMyData(1L);

		assertThat(aiResultRepository.findAll()).isEmpty();
		ActivityRecord reloadedDone = activityRecordRepository.findById(done.getId()).orElseThrow();
		assertThat(reloadedDone.getAiStatus()).isEqualTo(AiStatus.NONE);
		assertThat(reloadedDone.getAiAnalysis()).isNull();

		ActivityRecord reloadedSkipped = activityRecordRepository.findById(skipped.getId()).orElseThrow();
		assertThat(reloadedSkipped.getAiStatus()).isEqualTo(AiStatus.SKIPPED);
	}

	@Test
	void resetMyData_doesNotAffectOtherUsers() {
		Long hobbyId = hobbyRepository.save(new Hobby(2L, "피아노", null)).getId();
		ActivityRecord other = new ActivityRecord(2L, hobbyId, LocalDate.now(), "연습", null, null, null, false);
		other.markAnalysisDone("{\"title\":\"제목\"}");
		activityRecordRepository.save(other);

		service().resetMyData(1L);

		ActivityRecord reloaded = activityRecordRepository.findById(other.getId()).orElseThrow();
		assertThat(reloaded.getAiStatus()).isEqualTo(AiStatus.DONE);
	}
}
