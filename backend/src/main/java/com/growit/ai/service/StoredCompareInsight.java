package com.growit.ai.service;

import java.util.List;

/** ai_results.result 안에 저장되는 항목(근거는 id만 저장하고, 날짜·내용은 응답을 만들 때 서버가 채운다). */
record StoredCompareInsight(String point, String because, List<Long> evidenceRecordIds) {
}
