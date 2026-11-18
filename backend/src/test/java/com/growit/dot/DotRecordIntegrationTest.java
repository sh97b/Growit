package com.growit.dot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.dot.dto.DotGoalResponse;
import com.growit.dot.dto.DotGoalStartRequest;
import com.growit.dot.entity.DotGoalState;
import com.growit.dot.repository.DotGoalRepository;
import com.growit.dot.repository.DotProgressRepository;
import com.growit.dot.service.DotGoalService;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordUpdateRequest;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.record.service.RecordService;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제로 커밋이 일어나야 확인할 수 있는 것들: 기록 저장과 도트 칠하기가 서로의 트랜잭션을 오염시키지 않는지,
 * 동시에 시작해도 진행 중인 그림이 하나뿐인지. {@code @DataJpaTest}는 롤백해서 이 둘을 증명하지 못해 {@code @SpringBootTest}를 쓴다.
 */
@SpringBootTest
class DotRecordIntegrationTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@Autowired
	private RecordService recordService;

	@Autowired
	private DotGoalService dotGoalService;

	@Autowired
	private ActivityRecordRepository recordRepository;

	@Autowired
	private DotGoalRepository goalRepository;

	@SpyBean
	private DotProgressRepository progressRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private Long userId;
	private Long hobbyId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("dot-it-" + System.nanoTime() + "@example.com", "hashed", "사용자"))
				.getId();
		hobbyId = hobbyRepository.save(new Hobby(userId, "기타", null)).getId();
	}

	@AfterEach
	void tearDown() {
		Mockito.reset(progressRepository);
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			progressRepository.deleteByUserId(userId);
			goalRepository.deleteByUserId(userId);
			recordRepository.deleteAll(recordRepository.findByUserId(userId));
			hobbyRepository.deleteByUserId(userId);
			userRepository.deleteById(userId);
		});
	}

	private RecordResponse createRecord(LocalDate date) {
		return recordService.create(userId, new RecordCreateRequest(hobbyId, date, "연습했어요", null, null, null, null));
	}

	private DotGoalResponse startGoal(int days) {
		return dotGoalService.start(userId, new DotGoalStartRequest("dumbbell", days));
	}

	@Test
	void createRecord_paintsTodaysCell_afterCommit() {
		DotGoalResponse goal = startGoal(14);

		createRecord(TODAY);
		createRecord(TODAY);

		assertThat(progressRepository.countByGoalId(goal.id())).isEqualTo(1);
	}

	@Test
	void createRecord_whenPaintingBlowsUp_recordIsStillSaved_andNextCurrentCallRestoresTheDay() {
		DotGoalResponse goal = startGoal(14);
		doAnswer(invocation -> {
			throw new IllegalStateException("칠하기 중 일부러 낸 오류");
		}).when(progressRepository).save(any());

		RecordResponse created = assertThatCodeDoesNotThrow(() -> createRecord(TODAY));

		assertThat(recordRepository.findById(created.id())).isPresent();
		assertThat(progressRepository.countByGoalId(goal.id())).isZero();
		Mockito.reset(progressRepository);

		DotGoalResponse restored = dotGoalService.current(userId).goal();

		assertThat(restored.filledCount()).isEqualTo(1);
		assertThat(progressRepository.findDatesByGoalId(goal.id())).containsExactly(TODAY);
	}

	@Test
	void createRecord_whenPaintingBlowsUp_followingRecordsStillWorkInNormalTransactions() {
		DotGoalResponse goal = startGoal(14);
		doAnswer(invocation -> {
			throw new IllegalStateException("일부러 낸 오류");
		}).when(progressRepository).save(any());

		createRecord(TODAY);
		Mockito.reset(progressRepository);
		RecordResponse second = createRecord(TODAY);

		assertThat(recordRepository.findById(second.id())).isPresent();
		assertThat(recordRepository.findByUserId(userId)).hasSize(2);
		assertThat(progressRepository.countByGoalId(goal.id())).isEqualTo(1);
	}

	@Test
	void deleteRecord_keepsAlreadyPaintedCell() {
		DotGoalResponse goal = startGoal(14);
		RecordResponse created = createRecord(TODAY);

		recordService.delete(userId, created.id());

		assertThat(recordRepository.findById(created.id())).isEmpty();
		assertThat(progressRepository.countByGoalId(goal.id())).isEqualTo(1);
		assertThat(dotGoalService.current(userId).goal().filledCount()).isEqualTo(1);
	}

	@Test
	void updateRecord_changingDateToStartDay_paintsIt_butOtherUpdatesDoNot() {
		DotGoalResponse goal = startGoal(14);
		RecordResponse created = createRecord(TODAY.minusDays(1));
		assertThat(progressRepository.countByGoalId(goal.id())).isZero();

		recordService.update(userId, created.id(), new RecordUpdateRequest(null, null, JsonNullable.undefined(),
				JsonNullable.of(7), JsonNullable.undefined(), JsonNullable.undefined(), null));
		assertThat(progressRepository.countByGoalId(goal.id())).isZero();

		recordService.update(userId, created.id(), new RecordUpdateRequest(null, TODAY, JsonNullable.undefined(),
				JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), null));
		assertThat(progressRepository.findDatesByGoalId(goal.id())).containsExactly(TODAY);
	}

	@Test
	void updateRecord_movingPaintedRecordToAnEarlierDay_doesNotUnpaint() {
		DotGoalResponse goal = startGoal(14);
		RecordResponse created = createRecord(TODAY);

		recordService.update(userId, created.id(), new RecordUpdateRequest(null, TODAY.minusDays(2),
				JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(),
				JsonNullable.undefined(), null));

		assertThat(progressRepository.findDatesByGoalId(goal.id())).containsExactly(TODAY);
	}

	@Test
	void concurrentStart_onlyOneSucceeds_theOtherGetsConflict() throws Exception {
		int threads = 6;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger created = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		List<Future<Void>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<Void> task = () -> {
				ready.countDown();
				go.await();
				try {
					startGoal(14);
					created.incrementAndGet();
				} catch (ApiException e) {
					if (e.getCode() == ErrorCode.DOT_GOAL_ACTIVE) {
						conflicts.incrementAndGet();
					} else {
						throw e;
					}
				}
				return null;
			};
			futures.add(pool.submit(task));
		}
		ready.await(5, TimeUnit.SECONDS);
		go.countDown();
		for (Future<Void> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}
		pool.shutdownNow();

		assertThat(created.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(threads - 1);
		assertThat(goalRepository.findAll().stream()
				.filter(g -> g.getUserId().equals(userId) && g.getState() == DotGoalState.ACTIVE)).hasSize(1);
	}

	private static <T> T assertThatCodeDoesNotThrow(Callable<T> call) {
		List<T> holder = new ArrayList<>();
		assertThatCode(() -> holder.add(call.call())).doesNotThrowAnyException();
		return holder.get(0);
	}
}
