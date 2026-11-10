package com.growit.notification.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.notification.dto.NotificationSettingsResponse;
import com.growit.notification.dto.NotificationSettingsUpdateRequest;
import com.growit.notification.entity.NotificationSettings;
import com.growit.notification.repository.NotificationSettingsRepository;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationSettingsService {

	private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
	private static final Pattern DAYS_PATTERN = Pattern.compile("^[01]{7}$");

	private final NotificationSettingsRepository notificationSettingsRepository;

	public NotificationSettingsService(NotificationSettingsRepository notificationSettingsRepository) {
		this.notificationSettingsRepository = notificationSettingsRepository;
	}

	@Transactional
	public NotificationSettingsResponse get(Long userId) {
		return toResponse(findOrCreate(userId));
	}

	@Transactional
	public NotificationSettingsResponse update(Long userId, NotificationSettingsUpdateRequest request) {
		validate(request);
		NotificationSettings settings = findOrCreate(userId);
		settings.update(request.enabled(), request.timeOfDay(), request.daysOfWeek(), request.notifyOnCoachArrival());
		return toResponse(settings);
	}

	private void validate(NotificationSettingsUpdateRequest request) {
		if (request.timeOfDay() == null || !TIME_PATTERN.matcher(request.timeOfDay()).matches()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "timeOfDay는 HH:mm 형식이어야 해요");
		}
		if (request.daysOfWeek() == null || !DAYS_PATTERN.matcher(request.daysOfWeek()).matches()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "daysOfWeek는 0과 1로 된 7글자여야 해요(월~일)");
		}
	}

	private NotificationSettings findOrCreate(Long userId) {
		return notificationSettingsRepository.findByUserId(userId)
				.orElseGet(() -> notificationSettingsRepository.save(new NotificationSettings(userId)));
	}

	private NotificationSettingsResponse toResponse(NotificationSettings settings) {
		return new NotificationSettingsResponse(settings.isEnabled(), settings.getTimeOfDay(),
				settings.getDaysOfWeek(), settings.isNotifyOnCoachArrival());
	}
}
