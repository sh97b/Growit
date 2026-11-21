package com.growit.record.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * "이 항목을 해 본 것 같아요" 제안. 항목 문장은 저장하지 않고 보여 줄 때 로드맵에서 읽는다(사용자가 고쳐도 최신 문장).
 * answer는 아직 답하지 않았으면 null, "CHECKED"(체크해 둠) 또는 "DISMISSED"(아니에요)다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record StoredRoadmapSuggestion(Long itemId, String answer) {
}
