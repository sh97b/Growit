package com.growit.hobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.ApiException;
import com.growit.common.ErrorCode;
import com.growit.hobby.dto.HobbyCreateRequest;
import com.growit.hobby.dto.HobbyResponse;
import com.growit.hobby.dto.HobbyUpdateRequest;
import com.growit.record.ActivityRecord;
import com.growit.record.ActivityRecordRepository;
import com.growit.record.Feeling;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class HobbyServiceTest {

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	private HobbyService hobbyService() {
		return new HobbyService(hobbyRepository, activityRecordRepository);
	}

	@Test
	void create_savesHobby() {
		HobbyService service = hobbyService();

		HobbyResponse response = service.create(1L, new HobbyCreateRequest("기타", "코드 전환 연습"));

		assertThat(response.id()).isNotNull();
		assertThat(response.name()).isEqualTo("기타");
		assertThat(response.goal()).isEqualTo("코드 전환 연습");
		assertThat(response.recordCount()).isZero();
	}

	@Test
	void create_duplicateNameForSameUser_throwsHobbyNameTaken() {
		HobbyService service = hobbyService();
		service.create(1L, new HobbyCreateRequest("기타", null));

		assertThatThrownBy(() -> service.create(1L, new HobbyCreateRequest("기타", null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.HOBBY_NAME_TAKEN));
	}

	@Test
	void create_sameNameForDifferentUsers_succeeds() {
		HobbyService service = hobbyService();
		service.create(1L, new HobbyCreateRequest("기타", null));

		HobbyResponse response = service.create(2L, new HobbyCreateRequest("기타", null));

		assertThat(response.id()).isNotNull();
	}

	@Test
	void update_otherUsersHobby_throwsNotFound() {
		HobbyService service = hobbyService();
		HobbyResponse created = service.create(1L, new HobbyCreateRequest("기타", null));

		assertThatThrownBy(() -> service.update(2L, created.id(), new HobbyUpdateRequest("피아노", null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void delete_hobbyWithRecords_throwsHobbyHasRecords() {
		HobbyService service = hobbyService();
		HobbyResponse created = service.create(1L, new HobbyCreateRequest("기타", null));
		activityRecordRepository.save(new ActivityRecord(1L, created.id(), LocalDate.now(), "연습했다", 10, null,
				Feeling.PROUD, false));

		assertThatThrownBy(() -> service.delete(1L, created.id()))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.HOBBY_HAS_RECORDS));
	}

	@Test
	void delete_hobbyWithoutRecords_succeeds() {
		HobbyService service = hobbyService();
		HobbyResponse created = service.create(1L, new HobbyCreateRequest("기타", null));

		service.delete(1L, created.id());

		assertThat(hobbyRepository.findByIdAndUserId(created.id(), 1L)).isEmpty();
	}
}
