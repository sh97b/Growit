package com.growit.record.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * text는 구체적 변화, support는 인정·응원·위로 한 문장이다. 예전 결과에는 support가 없고, 새 결과에서는 한쪽만
 * 검증을 통과하면 다른 쪽은 null이다(둘 다 null이면 change 자체를 저장하지 않는다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
record StoredChange(String text, Long evidenceRecordId, String support) {
}
