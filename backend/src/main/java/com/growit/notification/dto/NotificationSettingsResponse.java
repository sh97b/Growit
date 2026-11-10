package com.growit.notification.dto;

public record NotificationSettingsResponse(
		boolean enabled,
		String timeOfDay,
		String daysOfWeek,
		boolean notifyOnCoachArrival) {
}
