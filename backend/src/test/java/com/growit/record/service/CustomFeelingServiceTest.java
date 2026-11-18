package com.growit.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.dto.CustomFeelingRequest;
import com.growit.record.dto.CustomFeelingResponse;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordUpdateRequest;
import com.growit.record.entity.Feeling;
import com.growit.record.entity.FeelingDirection;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.record.repository.CustomFeelingRepository;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;

@DataJpaTest
class CustomFeelingServiceTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@Autowired
	private CustomFeelingRepository repository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private ActivityRecordRepository recordRepository;

	private CustomFeelingService service;
	private Long userId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		service = new CustomFeelingService(repository);
		userId = userRepository.save(new User("feel-a@example.com", "hash", "에이")).getId();
		otherUserId = userRepository.save(new User("feel-b@example.com", "hash", "비")).getId();
	}

	private CustomFeelingResponse add(Long owner, String label, FeelingDirection direction) {
		return service.create(owner, new CustomFeelingRequest(label, direction));
	}

	private void assertInvalid(String label) {
		assertThatThrownBy(() -> add(userId, label, FeelingDirection.GOOD)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void create_validLabel_isSavedTrimmedWithDirection_andListedInOrder() {
		CustomFeelingResponse first = add(userId, "  설렘  ", FeelingDirection.GOOD);
		add(userId, "Tired 1", FeelingDirection.HARD);

		assertThat(first.label()).isEqualTo("설렘");
		assertThat(first.direction()).isEqualTo(FeelingDirection.GOOD);
		assertThat(service.list(userId)).extracting(CustomFeelingResponse::label).containsExactly("설렘", "Tired 1");
	}

	@Test
	void create_blankTooLongOrBadCharacters_areRejected() {
		assertInvalid(null);
		assertInvalid("");
		assertInvalid("   ");
		assertInvalid("가나다라마바사아자"); // 9자
		assertInvalid("설렘!");
		assertInvalid("a_b");
		assertInvalid("😀");
		assertInvalid("설\n렘");
		assertThat(service.list(userId)).isEmpty();
	}

	@Test
	void create_exactlyEightCharacters_isAccepted() {
		assertThat(add(userId, "가나다라마바사아", FeelingDirection.NEUTRAL).label()).hasSize(8);
	}

	@Test
	void create_builtInOrOwnDuplicate_isRejected_caseInsensitively() {
		for (String builtIn : new String[] { "뿌듯함", "답답함", "개운함", "담담함", "잘 모르겠음" }) {
			assertInvalid(builtIn);
		}
		add(userId, "Happy", FeelingDirection.GOOD);
		assertInvalid("happy");
		assertInvalid(" HAPPY ");
	}

	@Test
	void create_sameLabelForAnotherUser_isAllowed() {
		add(userId, "설렘", FeelingDirection.GOOD);

		assertThat(add(otherUserId, "설렘", FeelingDirection.HARD).direction()).isEqualTo(FeelingDirection.HARD);
	}

	@Test
	void create_overTwelve_isRejected() {
		for (int i = 0; i < 12; i++) {
			add(userId, "감정" + i, FeelingDirection.NEUTRAL);
		}

		assertInvalid("열세번째");
		assertThat(service.list(userId)).hasSize(12);
		assertThat(add(otherUserId, "다른사람", FeelingDirection.GOOD).id()).isNotNull();
	}

	@Test
	void delete_ownFeeling_removesIt_andOthersFeelingIs404() {
		CustomFeelingResponse mine = add(userId, "설렘", FeelingDirection.GOOD);
		CustomFeelingResponse theirs = add(otherUserId, "설렘", FeelingDirection.GOOD);

		assertThatThrownBy(() -> service.delete(userId, theirs.id())).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
		service.delete(userId, mine.id());

		assertThat(service.list(userId)).isEmpty();
		assertThat(service.list(otherUserId)).hasSize(1);
	}

	private RecordService recordService() {
		return new RecordService(recordRepository, hobbyRepository, userRepository, new com.growit.ai.config.AiProperties(),
				Mockito.mock(ApplicationEventPublisher.class), Mockito.mock(RecordAnalysisService.class),
				Mockito.mock(com.growit.dot.service.DotGoalService.class), service);
	}

	private Long hobbyOf(Long owner) {
		return hobbyRepository.save(new Hobby(owner, "기타", null)).getId();
	}

	@Test
	void record_withCustomFeeling_keepsLabelAndDirection_evenAfterTheFeelingIsDeleted() {
		Long hobbyId = hobbyOf(userId);
		CustomFeelingResponse feeling = add(userId, "설렘", FeelingDirection.GOOD);

		RecordResponse created = recordService().create(userId,
				new RecordCreateRequest(hobbyId, TODAY, "연습", null, null, null, null, feeling.id()));
		service.delete(userId, feeling.id());
		RecordResponse reread = recordService().get(userId, created.id());

		assertThat(created.feeling()).isNull();
		assertThat(created.feelingLabel()).isEqualTo("설렘");
		assertThat(created.feelingDirection()).isEqualTo("GOOD");
		assertThat(reread.feelingLabel()).isEqualTo("설렘");
		assertThat(reread.feelingDirection()).isEqualTo("GOOD");
	}

	@Test
	void record_withBothBuiltInAndCustomFeeling_isRejected() {
		Long hobbyId = hobbyOf(userId);
		CustomFeelingResponse feeling = add(userId, "설렘", FeelingDirection.GOOD);

		assertThatThrownBy(() -> recordService().create(userId,
				new RecordCreateRequest(hobbyId, TODAY, "연습", null, null, Feeling.PROUD, null, feeling.id())))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void record_withAnotherUsersCustomFeeling_is404() {
		Long hobbyId = hobbyOf(userId);
		CustomFeelingResponse theirs = add(otherUserId, "설렘", FeelingDirection.GOOD);

		assertThatThrownBy(() -> recordService().create(userId,
				new RecordCreateRequest(hobbyId, TODAY, "연습", null, null, null, null, theirs.id())))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void record_builtInFeelingStillWorks_andUpdateSwitchesBetweenBuiltInAndCustom() {
		Long hobbyId = hobbyOf(userId);
		CustomFeelingResponse feeling = add(userId, "설렘", FeelingDirection.GOOD);
		RecordResponse created = recordService().create(userId,
				new RecordCreateRequest(hobbyId, TODAY, "연습", null, null, Feeling.STUCK, null));
		assertThat(created.feeling()).isEqualTo(Feeling.STUCK);
		assertThat(created.feelingLabel()).isNull();

		RecordResponse toCustom = recordService().update(userId, created.id(), new RecordUpdateRequest(null, null,
				JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), null,
				JsonNullable.of(feeling.id())));
		assertThat(toCustom.feeling()).isNull();
		assertThat(toCustom.feelingLabel()).isEqualTo("설렘");

		RecordResponse toBuiltIn = recordService().update(userId, created.id(), new RecordUpdateRequest(null, null,
				JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(),
				JsonNullable.of(Feeling.PROUD), null));
		assertThat(toBuiltIn.feeling()).isEqualTo(Feeling.PROUD);
		assertThat(toBuiltIn.feelingLabel()).isNull();

		RecordResponse cleared = recordService().update(userId, created.id(), new RecordUpdateRequest(null, null,
				JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(),
				JsonNullable.of((Feeling) null), null));
		assertThat(cleared.feeling()).isNull();
		assertThat(cleared.feelingLabel()).isNull();
	}
}
