package com.growit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.notification.dto.NotificationSettingsResponse;
import com.growit.notification.dto.NotificationSettingsUpdateRequest;
import com.growit.notification.repository.NotificationSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class NotificationSettingsServiceTest {

	@Autowired
	private NotificationSettingsRepository notificationSettingsRepository;

	private NotificationSettingsService service() {
		return new NotificationSettingsService(notificationSettingsRepository);
	}

	@Test
	void get_noSettingsYet_createsDefault() {
		NotificationSettingsResponse response = service().get(1L);

		assertThat(response.enabled()).isFalse();
		assertThat(response.timeOfDay()).isEqualTo("22:00");
		assertThat(response.daysOfWeek()).isEqualTo("1111111");
		assertThat(response.notifyOnCoachArrival()).isTrue();
	}

	@Test
	void update_savesNewValues() {
		NotificationSettingsService service = service();
		service.get(1L);

		NotificationSettingsResponse response = service.update(1L,
				new NotificationSettingsUpdateRequest(true, "08:30", "1111100", false));

		assertThat(response.enabled()).isTrue();
		assertThat(response.timeOfDay()).isEqualTo("08:30");
		assertThat(response.daysOfWeek()).isEqualTo("1111100");
		assertThat(response.notifyOnCoachArrival()).isFalse();
	}

	@Test
	void update_invalidTimeFormat_throwsValidationError() {
		NotificationSettingsService service = service();

		assertThatThrownBy(() -> service.update(1L, new NotificationSettingsUpdateRequest(true, "9:30", "1111111", true)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_invalidDaysOfWeek_throwsValidationError() {
		NotificationSettingsService service = service();

		assertThatThrownBy(() -> service.update(1L, new NotificationSettingsUpdateRequest(true, "09:30", "111111", true)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void differentUsers_haveSeparateSettings() {
		NotificationSettingsService service = service();
		service.update(1L, new NotificationSettingsUpdateRequest(true, "08:00", "1111111", true));

		NotificationSettingsResponse other = service.get(2L);

		assertThat(other.enabled()).isFalse();
		assertThat(other.timeOfDay()).isEqualTo("22:00");
	}
}
