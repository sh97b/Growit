package com.growit.notification.dto;

public record NotificationSettingsUpdateRequest(
		boolean enabled,
		String timeOfDay,
		String daysOfWeek,
		boolean notifyOnCoachArrival) {
}
