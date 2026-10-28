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
				new RecordUpdateRequest(null, null, "   ", null, null, null, null)))
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
				new RecordUpdateRequest(null, null, null, -5, null, null, null)))
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
}
