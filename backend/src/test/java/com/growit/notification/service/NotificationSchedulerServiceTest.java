package com.growit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.growit.hobby.repository.HobbyRepository;
import com.growit.notification.config.WebPushProperties;
import com.growit.notification.dto.NotificationSettingsUpdateRequest;
import com.growit.notification.entity.NotificationSettings;
import com.growit.notification.repository.NotificationSettingsRepository;
import com.growit.notification.repository.PushSubscriptionRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class NotificationSchedulerServiceTest {

	@Autowired
	private NotificationSettingsRepository notificationSettingsRepository;

	@Autowired
	private PushSubscriptionRepository pushSubscriptionRepository;

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	private NotificationSchedulerService service(boolean pushEnabled) {
		WebPushProperties properties = new WebPushProperties();
		if (pushEnabled) {
			properties.setPublicKey("pub");
			properties.setPrivateKey("priv");
			properties.setSubject("mailto:test@example.com");
		}
		return new NotificationSchedulerService(notificationSettingsRepository, pushSubscriptionRepository,
				activityRecordRepository, new PushSenderService(properties));
	}

	private Long enableSettings(Long userId, String timeOfDay, String daysOfWeek) {
		NotificationSettingsService settingsService = new NotificationSettingsService(notificationSettingsRepository);
		settingsService.update(userId, new NotificationSettingsUpdateRequest(true, timeOfDay, daysOfWeek, true));
		return userId;
	}

	@Test
	void sendDueReminders_pushDisabled_doesNothing() {
		enableSettings(1L, "09:00", "1111111");

		service(false).sendDueReminders();

		NotificationSettings settings = notificationSettingsRepository.findByUserId(1L).orElseThrow();
		assertThat(settings.getLastSentDate()).isNull();
	}

	@Test
	void sendDueReminders_timeDoesNotMatch_doesNotMarkSent() {
		enableSettings(1L, "09:00", "1111111");

		service(true).sendDueReminders();

		NotificationSettings settings = notificationSettingsRepository.findByUserId(1L).orElseThrow();
		assertThat(settings.getLastSentDate()).isNull();
	}

	@Test
	void sendDueReminders_alreadyRecordedTodayAndRunsAtTheirTime_marksSentButSkipsSend() {
		Long hobbyId = hobbyRepository.save(new com.growit.hobby.entity.Hobby(1L, "기타", null)).getId();
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
		activityRecordRepository.save(new ActivityRecord(1L, hobbyId, today, "연습", 10, null, Feeling.PROUD, false));

		String nowTime = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
		enableSettings(1L, nowTime, "1111111");

		service(true).sendDueReminders();

		NotificationSettings settings = notificationSettingsRepository.findByUserId(1L).orElseThrow();
		assertThat(settings.getLastSentDate()).isEqualTo(today);
	}

	@Test
	void sendDueReminders_alreadySentToday_doesNotRunAgain() {
		NotificationSettings settings = notificationSettingsRepository.save(new NotificationSettings(1L));
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
		settings.update(true, "00:00", "1111111", true);
		settings.markSent(today);
		notificationSettingsRepository.save(settings);

		// 시각 매칭 로직을 건드리지 않고도 lastSentDate가 이미 오늘이면 더 나아가지 않는지만 확인한다.
		service(true).sendDueReminders();

		NotificationSettings reloaded = notificationSettingsRepository.findByUserId(1L).orElseThrow();
		assertThat(reloaded.getLastSentDate()).isEqualTo(today);
	}

	@Test
	void notifyCoachArrival_noSubscriptions_doesNotThrow() {
		enableSettings(1L, "09:00", "1111111");

		service(true).notifyCoachArrival(1L);
	}

	@Test
	void notifyCoachArrival_disabledSettings_doesNothing() {
		service(true).notifyCoachArrival(999L);
	}
}
