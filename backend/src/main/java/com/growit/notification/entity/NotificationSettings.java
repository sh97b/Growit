package com.growit.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "time_of_day", nullable = false, length = 5)
	private String timeOfDay = "22:00";

	/** 월~일 순서의 7글자, '1'이면 그 요일에 보낸다(예: "1111100"은 월~금). */
	@Column(name = "days_of_week", nullable = false, length = 7)
	private String daysOfWeek = "1111111";

	@Column(name = "notify_on_coach_arrival", nullable = false)
	private boolean notifyOnCoachArrival = true;

	/** 같은 날 두 번 보내지 않으려고 스케줄러가 기록하는 마지막 발송 날짜. */
	@Column(name = "last_sent_date")
	private LocalDate lastSentDate;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	protected NotificationSettings() {
	}

	public NotificationSettings(Long userId) {
		this.userId = userId;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getTimeOfDay() {
		return timeOfDay;
	}

	public String getDaysOfWeek() {
		return daysOfWeek;
	}

	public boolean isNotifyOnCoachArrival() {
		return notifyOnCoachArrival;
	}

	public LocalDate getLastSentDate() {
		return lastSentDate;
	}

	public void markSent(LocalDate date) {
		this.lastSentDate = date;
	}

	public void update(boolean enabled, String timeOfDay, String daysOfWeek, boolean notifyOnCoachArrival) {
		this.enabled = enabled;
		this.timeOfDay = timeOfDay;
		this.daysOfWeek = daysOfWeek;
		this.notifyOnCoachArrival = notifyOnCoachArrival;
	}
}
