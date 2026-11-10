package com.growit.notification.dto;

public record PushSubscribeRequest(String endpoint, Keys keys) {

	public record Keys(String p256dh, String auth) {
	}
}
