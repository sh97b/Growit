package com.growit.record.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.growit.ai.client.AiChatClient;
import com.growit.ai.client.AiChatResponse;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordResponse;
import com.growit.record.entity.AiStatus;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.record.service.RecordService;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * 트랜잭션 커밋 전에는 리스너가 돌지 않고, 커밋 뒤 별도 스레드에서 분석이 끝나는 것을 확인한다.
 * {@code @DataJpaTest}는 기본적으로 롤백하므로 실제 커밋이 필요한 이 테스트는 {@code @SpringBootTest}를 쓴다.
 */
@SpringBootTest
@TestPropertySource(properties = {"app.ai.api-key=test-key", "app.ai.model=test-model"})
class RecordAnalysisAsyncIntegrationTest {

	@Autowired
	private RecordService recordService;

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private UserRepository userRepository;

	@MockBean
	private AiChatClient aiChatClient;

	@Test
	void recordSavedEvent_runsAnalysisAfterCommitOnSeparateThread() throws InterruptedException {
		given(aiChatClient.complete(anyString(), anyString())).willReturn(new AiChatResponse(
				"{\"title\":\"코드 전환 연습\",\"tags\":[\"코드전환\"],\"relatedRecordIds\":[],\"change\":null}", 5, "model"));

		Long userId = userRepository.save(new User("async-test@example.com", "hashed", "사용자")).getId();
		Long hobbyId = hobbyRepository.save(new Hobby(userId, "기타", null)).getId();

		RecordResponse created = recordService.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "코드 전환 연습", null, null, null, null));

		// 커밋 직후, 비동기 작업은 아직 끝나지 않았을 수 있다 - 응답 자체는 AI와 무관하게 바로 와야 한다.
		assertThat(created.aiStatus()).isEqualTo(AiStatus.PENDING);

		AiStatus finalStatus = waitForStatusChange(created.id());

		assertThat(finalStatus).isEqualTo(AiStatus.DONE);
	}

	private AiStatus waitForStatusChange(Long recordId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5000;
		AiStatus status;
		do {
			status = activityRecordRepository.findById(recordId).orElseThrow().getAiStatus();
			if (status != AiStatus.PENDING) {
				return status;
			}
			Thread.sleep(50);
		} while (System.currentTimeMillis() < deadline);
		return status;
	}
}
