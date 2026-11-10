package com.growit.notification.service;

public enum PushSendResult {
	OK,
	/** 구독이 만료·해지됨(410/404) — 호출한 쪽에서 이 구독을 지워야 한다. */
	GONE,
	FAILED,
	DISABLED
}
