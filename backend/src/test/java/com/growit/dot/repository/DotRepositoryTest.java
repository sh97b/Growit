package com.growit.dot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.growit.dot.entity.DotGoal;
import com.growit.dot.entity.DotGoalState;
import com.growit.dot.entity.DotProgress;
import com.growit.dot.entity.DotSourceType;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.LocalDate;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class DotRepositoryTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 10);

	@Autowired
	private DotGoalRepository goalRepository;

	@Autowired
	private DotProgressRepository progressRepository;

	@Autowired
	private ActivityRecordRepository recordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	private Long userId;
	private Long otherUserId;
	private Long hobbyId;
	private Long otherHobbyId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("dot-a@example.com", "hash", "에이")).getId();
		otherUserId = userRepository.save(new User("dot-b@example.com", "hash", "비")).getId();
		hobbyId = hobbyRepository.save(new Hobby(userId, "기타", null)).getId();
		otherHobbyId = hobbyRepository.save(new Hobby(otherUserId, "독서", null)).getId();
	}

	private DotGoal newGoal(Long owner) {
		return goalRepository.save(new DotGoal(owner, "기타 그림", DotSourceType.PRESET, "guitar", 30, 9, 9,
				"[{\"x\":0,\"y\":0,\"color\":0}]", "[\"#112233\"]", START));
	}

	private void record(Long owner, Long hobby, LocalDate date) {
		recordRepository.save(new ActivityRecord(owner, hobby, date, "연습", null, null, null, false));
	}

	@Test
	void goal_savedWithJsonAndDefaults_andFoundByStateAndOwner() {
		DotGoal goal = newGoal(userId);

		DotGoal found = goalRepository.findFirstByUserIdAndState(userId, DotGoalState.ACTIVE).orElseThrow();
		assertThat(found.getId()).isEqualTo(goal.getId());
		assertThat(found.isCompletionSeen()).isFalse();
		assertThat(found.getStartedOn()).isEqualTo(START);
		assertThat(found.getGridJson()).contains("color");
		assertThat(goalRepository.findByIdAndUserId(goal.getId(), otherUserId)).isEmpty();
		assertThat(goalRepository.findFirstByUserIdAndState(otherUserId, DotGoalState.ACTIVE)).isEmpty();
	}

	@Test
	void goal_completeAndStop_changeState() {
		DotGoal completed = newGoal(userId);
		completed.complete(START.plusDays(40));
		completed.markCompletionSeen();
		goalRepository.saveAndFlush(completed);
		DotGoal stopped = newGoal(userId);
		stopped.stop();
		goalRepository.saveAndFlush(stopped);

		List<DotGoal> album = goalRepository.findByUserIdAndStateOrderByCompletedOnDescIdDesc(userId,
				DotGoalState.COMPLETED);
		assertThat(album).extracting(DotGoal::getId).containsExactly(completed.getId());
		assertThat(album.get(0).getCompletedOn()).isEqualTo(START.plusDays(40));
		assertThat(album.get(0).isCompletionSeen()).isTrue();
		assertThat(goalRepository.findFirstByUserIdAndState(userId, DotGoalState.ACTIVE)).isEmpty();
	}

	@Test
	void goal_findByIdForUpdate_returnsRow() {
		DotGoal goal = newGoal(userId);

		assertThat(goalRepository.findByIdForUpdate(goal.getId())).isPresent();
	}

	@Test
	void progress_compositeKey_countsAndDetectsDates() {
		DotGoal goal = newGoal(userId);
		progressRepository.save(new DotProgress(goal.getId(), START));
		progressRepository.save(new DotProgress(goal.getId(), START.plusDays(2)));
		progressRepository.flush();

		assertThat(progressRepository.countByGoalId(goal.getId())).isEqualTo(2);
		assertThat(progressRepository.existsByGoalIdAndRecordDate(goal.getId(), START)).isTrue();
		assertThat(progressRepository.existsByGoalIdAndRecordDate(goal.getId(), START.plusDays(1))).isFalse();
		assertThat(progressRepository.findDatesByGoalId(goal.getId())).containsExactly(START, START.plusDays(2));
	}

	@Test
	void progress_savingSameDateTwice_staysOneRow() {
		DotGoal goal = newGoal(userId);
		progressRepository.saveAndFlush(new DotProgress(goal.getId(), START));
		progressRepository.saveAndFlush(new DotProgress(goal.getId(), START));

		assertThat(progressRepository.countByGoalId(goal.getId())).isEqualTo(1);
	}

	@Test
	void findMissingDates_onlyFromStartOnward_onceEachDay_excludingFilled_andOnlyOwnRecords() {
		DotGoal goal = newGoal(userId);
		record(userId, hobbyId, START.minusDays(1));
		record(userId, hobbyId, START);
		record(userId, hobbyId, START);
		record(userId, hobbyId, START.plusDays(1));
		record(userId, hobbyId, START.plusDays(3));
		record(otherUserId, otherHobbyId, START.plusDays(2));
		progressRepository.saveAndFlush(new DotProgress(goal.getId(), START.plusDays(1)));

		List<LocalDate> missing = progressRepository.findMissingDates(userId, goal.getId(), START);

		assertThat(missing).containsExactly(START, START.plusDays(3));
	}

	@Test
	void findMissingDates_noRecords_empty() {
		DotGoal goal = newGoal(userId);

		assertThat(progressRepository.findMissingDates(userId, goal.getId(), START)).isEmpty();
	}

	@Test
	void deleteByUserId_removesOnlyThatUsersGoalsAndProgress() {
		DotGoal mine = newGoal(userId);
		DotGoal theirs = newGoal(otherUserId);
		progressRepository.save(new DotProgress(mine.getId(), START));
		progressRepository.save(new DotProgress(theirs.getId(), START));
		progressRepository.flush();

		progressRepository.deleteByUserId(userId);
		goalRepository.deleteByUserId(userId);
		goalRepository.flush();
		entityManager.clear();

		assertThat(goalRepository.findById(mine.getId())).isEmpty();
		assertThat(progressRepository.countByGoalId(mine.getId())).isZero();
		assertThat(goalRepository.findById(theirs.getId())).isPresent();
		assertThat(progressRepository.countByGoalId(theirs.getId())).isEqualTo(1);
	}
}
