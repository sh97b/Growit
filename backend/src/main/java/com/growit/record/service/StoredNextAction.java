package com.growit.record.service;

/** "다음에 해 볼 일". state는 아직 반응이 없으면 null, 사용자가 해 봤다고 하면 "TRIED". */
record StoredNextAction(String text, Integer minutes, String state) {

	StoredNextAction withState(String newState) {
		return new StoredNextAction(text, minutes, newState);
	}
}
