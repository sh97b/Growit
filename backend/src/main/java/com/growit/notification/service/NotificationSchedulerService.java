package com.growit.notification.service;

import com.growit.notification.entity.NotificationSettings;
import com.growit.notification.entity.PushSubscription;
import com.growit.notification.repository.NotificationSettingsRepository;
import com.growit.notification.repository.PushSubscriptionRepository;
import com.growit.record.repository.ActivityRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1분마다 Asia/Seoul 기준으로 지금 시각·요일이 설정과 같은 사용자를 찾아 알림을 보낸다. 단일 서버
 * 전제다(여러 인스턴스를 띄우면 같은 알림이 중복 발송될 수 있다 — 지금은 EC2 한 대만 운영하므로 괜찮다).
 */
@Service
public class NotificationSchedulerService {

	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
	private static final String REMINDER_PAYLOAD =
			"{\"title\":\"Growit\",\"body\":\"오늘 한 줄 남기고 싶을 때 열어 보세요.\"}";
	private static final String COACH_ARRIVAL_PAYLOAD =
			"{\"title\":\"Growit\",\"body\":\"새 회고가 도착했어요.\"}";

	private final NotificationSettingsRepository notificationSettingsRepository;
	private final PushSubscriptionRepository pushSubscriptionRepository;
	private final ActivityRecordRepository activityRecordRepository;
	private final PushSenderService pushSenderService;

	public NotificationSchedulerService(NotificationSettingsRepository notificationSettingsRepository,
			PushSubscriptionRepository pushSubscriptionRepository, ActivityRecordRepository activityRecordRepository,
			PushSenderService pushSenderService) {
		this.notificationSettingsRepository = notificationSettingsRepository;
		this.pushSubscriptionRepository = pushSubscriptionRepository;
		this.activityRecordRepository = activityRecordRepository;
		this.pushSenderService = pushSenderService;
	}

	@Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
	@Transactional
	public void sendDueReminders() {
		if (!pushSenderService.isEnabled()) {
			return;
		}
		LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
		LocalDate today = now.toLocalDate();
		String nowTime = now.format(HHMM);
		int dayIndex = now.getDayOfWeek().getValue() - 1;

		for (NotificationSettings settings : notificationSettingsRepository.findByEnabledTrue()) {
			if (today.equals(settings.getLastSentDate())) {
				continue;
			}
			if (!settings.getTimeOfDay().equals(nowTime)) {
				continue;
			}
			if (settings.getDaysOfWeek().charAt(dayIndex) != '1') {
				continue;
			}

			settings.markSent(today);
			LocalDate lastRecordDate = activityRecordRepository.findMaxRecordDateByUserId(settings.getUserId());
			if (today.equals(lastRecordDate)) {
				continue;
			}
			sendToAllDevices(settings.getUserId(), REMINDER_PAYLOAD);
		}
	}

	/** 주간 자동 회고가 만들어졌을 때 호출한다. 알림이 꺼져 있거나 설정 자체가 없으면 조용히 넘어간다. */
	@Transactional
	public void notifyCoachArrival(Long userId) {
		if (!pushSenderService.isEnabled()) {
			return;
		}
		notificationSettingsRepository.findByUserId(userId)
				.filter(NotificationSettings::isEnabled)
				.filter(NotificationSettings::isNotifyOnCoachArrival)
				.ifPresent(settings -> sendToAllDevices(userId, COACH_ARRIVAL_PAYLOAD));
	}

	private void sendToAllDevices(Long userId, String payload) {
		for (PushSubscription subscription : pushSubscriptionRepository.findByUserId(userId)) {
			PushSendResult result = pushSenderService.send(subscription, payload);
			if (result == PushSendResult.GONE) {
				pushSubscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
			}
		}
	}
}
