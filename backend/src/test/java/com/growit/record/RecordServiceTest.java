package com.growit.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.ApiException;
import com.growit.common.ErrorCode;
import com.growit.hobby.Hobby;
import com.growit.hobby.HobbyRepository;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordUpdateRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class RecordServiceTest {

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	private RecordService recordService() {
		return new RecordService(activityRecordRepository, hobbyRepository);
	}

	private Long createHobby(Long userId, String name) {
		Hobby hobby = new Hobby(userId, name, null);
		return hobbyRepository.save(hobby).getId();
	}

	@Test
	void create_savesRecord() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");

		RecordResponse response = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 28), "코드 전환 연습", 20, "속도가 안 나왔다",
						Feeling.PROUD, true));

		assertThat(response.id()).isNotNull();
		assertThat(response.hobbyId()).isEqualTo(hobbyId);
		assertThat(response.content()).isEqualTo("코드 전환 연습");
		assertThat(response.analysis()).isNull();
	}

	@Test
	void create_hobbyNotOwnedByUser_throwsNotFound() {
		RecordService service = recordService();
		Long hobbyId = createHobby(2L, "기타");

		assertThatThrownBy(() -> service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void get_otherUsersRecord_throwsNotFound() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.get(2L, created.id()))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void update_blankContent_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.of("   "), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_negativeMinutes_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.undefined(), JsonNullable.of(-5),
						JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void create_archivedHobby_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Hobby hobby = hobbyRepository.findById(hobbyId).orElseThrow();
		hobby.archive(java.time.LocalDateTime.now());
		hobbyRepository.save(hobby);

		assertThatThrownBy(() -> service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_moveToArchivedHobby_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		Long archivedHobbyId = createHobby(1L, "러닝");
		Hobby archivedHobby = hobbyRepository.findById(archivedHobbyId).orElseThrow();
		archivedHobby.archive(java.time.LocalDateTime.now());
		hobbyRepository.save(archivedHobby);

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(archivedHobbyId, null, JsonNullable.undefined(), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void list_filtersByUserAndHobby() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobbyId = createHobby(2L, "피아노");
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "연습 1", null, null, null, null));
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 2), "연습 2", null, null, null, null));
		service.create(2L,
				new RecordCreateRequest(otherHobbyId, LocalDate.of(2026, 10, 2), "남의 기록", null, null, null, null));

		RecordListResponse response = service.list(1L, null, null, null, null, null);

		assertThat(response.totalCount()).isEqualTo(2);
		assertThat(response.items()).extracting(RecordResponse::recordDate)
				.containsExactly(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1));
	}

	@Test
	void update_fieldsOmitted_keepsExistingValues() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", 20,
				"속도가 안 나왔다", Feeling.PROUD, false));

		RecordResponse updated = service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.undefined(), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null));

		assertThat(updated.minutes()).isEqualTo(20);
		assertThat(updated.difficulty()).isEqualTo("속도가 안 나왔다");
		assertThat(updated.feeling()).isEqualTo(Feeling.PROUD);
	}

	@Test
	void update_fieldsSetToNull_clearsValues() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", 20,
				"속도가 안 나왔다", Feeling.PROUD, false));

		RecordResponse updated = service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.undefined(), JsonNullable.of(null),
						JsonNullable.of(null), JsonNullable.of(null), null));

		assertThat(updated.minutes()).isNull();
		assertThat(updated.difficulty()).isNull();
		assertThat(updated.feeling()).isNull();
	}

	@Test
	void update_contentSetToNull_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.of(null), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_hobbyId_movesRecordToOwnedActiveHobby() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobbyId = createHobby(1L, "러닝");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		RecordResponse updated = service.update(1L, created.id(),
				new RecordUpdateRequest(otherHobbyId, null, JsonNullable.undefined(), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null));

		assertThat(updated.hobbyId()).isEqualTo(otherHobbyId);
	}

	@Test
	void create_futureDate_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");

		assertThatThrownBy(() -> service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now().plusDays(1), "연습했다", null, null, null, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_futureDate_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(null, LocalDate.now().plusDays(1), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void get_returnsPreviousAndNextRecordWithinSameHobby() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobbyId = createHobby(1L, "러닝");
		RecordResponse first = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "연습 1", null, null, null, null));
		RecordResponse middle = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 10), "연습 2", null, null, null, null));
		RecordResponse last = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 20), "연습 3", null, null, null, null));
		service.create(1L,
				new RecordCreateRequest(otherHobbyId, LocalDate.of(2026, 10, 15), "다른 분야 기록", null, null, null, null));

		RecordResponse response = service.get(1L, middle.id());

		assertThat(response.previousRecord().id()).isEqualTo(first.id());
		assertThat(response.nextRecord().id()).isEqualTo(last.id());

		RecordResponse firstResponse = service.get(1L, first.id());
		assertThat(firstResponse.previousRecord()).isNull();
		assertThat(firstResponse.nextRecord().id()).isEqualTo(middle.id());
	}

	@Test
	void list_doesNotIncludeNeighbors() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		RecordListResponse response = service.list(1L, null, null, null, null, null);

		assertThat(response.items().get(0).previousRecord()).isNull();
		assertThat(response.items().get(0).nextRecord()).isNull();
	}
}
