package com.growit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.ai.config.AiProperties;
import com.growit.ai.entity.AiLog;
import com.growit.ai.entity.AiLogState;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiLogRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class AiUsageServiceTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AiLogRepository aiLogRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long createUser() {
		return userRepository.save(new User("usage_test@example.com", "hashed", "사용자")).getId();
	}

	private AiProperties properties(int userLimit, int globalLimit) {
		AiProperties properties = new AiProperties();
		properties.setApiKey("test-key");
		properties.setModel("test-model");
		properties.setUserDailyLimit(userLimit);
		properties.setGlobalDailyLimit(globalLimit);
		return properties;
	}

	@Test
	void reserve_analysisUserLimitReached_throwsLimitExceededWithoutAffectingCompareCoachLimit() {
		Long userId = createUser();
		AiProperties properties = properties(5, 100);
		properties.setAnalysisUserDailyLimit(1);
		AiUsageService service = new AiUsageService(aiLogRepository, properties);

		service.reserve(userId, AiResultType.ANALYSIS);

		assertThatThrownBy(() -> service.reserve(userId, AiResultType.ANALYSIS))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));

		// COMPARE/COACH 한도는 ANALYSIS와 분리되어 그대로 쓸 수 있다.
		service.reserve(userId, AiResultType.COMPARE);
	}

	@Test
	void reserve_userLimitedType_blocksOnceLimitReached() {
		Long userId = createUser();
		AiUsageService service = new AiUsageService(aiLogRepository, properties(2, 100));

		service.reserve(userId, AiResultType.COMPARE);
		service.reserve(userId, AiResultType.COACH);

		assertThatThrownBy(() -> service.reserve(userId, AiResultType.COMPARE))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));

		assertThat(aiLogRepository.findAll()).hasSize(1);
		assertThat(aiLogRepository.findAll().get(0).getState()).isEqualTo(AiLogState.LIMIT.name());
	}

	@Test
	void reserve_analysisType_isNotCountedTowardUserLimit() {
		Long userId = createUser();
		AiUsageService service = new AiUsageService(aiLogRepository, properties(1, 100));

		service.reserve(userId, AiResultType.ANALYSIS);
		service.reserve(userId, AiResultType.ANALYSIS);

		assertThat(aiLogRepository.findAll()).isEmpty();
	}

	@Test
	void reserve_globalLimitReached_blocksEvenNonUserLimitedType() {
		Long userId = createUser();
		AiUsageService service = new AiUsageService(aiLogRepository, properties(100, 1));

		service.reserve(userId, AiResultType.ANALYSIS);

		assertThatThrownBy(() -> service.reserve(userId, AiResultType.WEEKLY))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_BUSY));
	}

	@Test
	void release_returnsReservedSlotSoNextReserveSucceeds() {
		Long userId = createUser();
		AiUsageService service = new AiUsageService(aiLogRepository, properties(1, 100));

		service.reserve(userId, AiResultType.COACH);
		service.release(userId, AiResultType.COACH);

		service.reserve(userId, AiResultType.COACH);
	}

	@Test
	void reserve_countsOnlyTodaysOkLogs() {
		Long userId = createUser();
		AiLog yesterdayLog = aiLogRepository.save(
				new AiLog(userId, AiResultType.COACH.name(), AiLogState.OK.name(), 100, 10, null));
		LocalDateTime yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1).atTime(12, 0);
		jdbcTemplate.update("update ai_logs set created_at = ? where id = ?", yesterday, yesterdayLog.getId());

		AiUsageService service = new AiUsageService(aiLogRepository, properties(1, 100));

		service.reserve(userId, AiResultType.COACH);
	}

	@Test
	void usage_reportsLimitUsedAndRemainingForTodayOnly() {
		Long userId = createUser();
		aiLogRepository.save(new AiLog(userId, AiResultType.COMPARE.name(), AiLogState.OK.name(), 100, 10, null));
		AiUsageService service = new AiUsageService(aiLogRepository, properties(5, 100));

		var usage = service.usage(userId);

		assertThat(usage.enabled()).isTrue();
		assertThat(usage.limit()).isEqualTo(5);
		assertThat(usage.used()).isEqualTo(1);
		assertThat(usage.remaining()).isEqualTo(4);
	}

	@Test
	void usage_reflectsDisabledWhenKeyOrModelMissing() {
		Long userId = createUser();
		AiProperties properties = new AiProperties();
		AiUsageService service = new AiUsageService(aiLogRepository, properties);

		assertThat(service.usage(userId).enabled()).isFalse();
	}
}
