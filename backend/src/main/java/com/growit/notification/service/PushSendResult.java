package com.growit.notification.service;

public enum PushSendResult {
	OK,
	/** 구독이 만료·해지됨(410/404) — 호출한 쪽에서 이 구독을 지워야 한다. */
	GONE,
	/** 저장된 p256dh·auth 형식이 깨짐(예전에 검증 없이 등록된 구독) — 호출한 쪽에서 이 구독을 지워야 한다. */
	INVALID,
	FAILED,
	DISABLED
}
