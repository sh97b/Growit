package com.growit.record.service;

/**
 * "다음에 해 볼 일". state는 아직 반응이 없으면 null, 사용자가 해 봤다고 하면 "TRIED".
 * reason·evidenceRecordId는 품질 보강(N2) 이후 결과에만 있고, 예전 결과는 null이라 그 줄만 생략된다.
 */
record StoredNextAction(String text, Integer minutes, String state, String reason, Long evidenceRecordId) {

	StoredNextAction(String text, Integer minutes, String state) {
		this(text, minutes, state, null, null);
	}

	StoredNextAction withState(String newState) {
		return new StoredNextAction(text, minutes, newState, reason, evidenceRecordId);
	}
}
