package com.growit.ai.service;

import java.util.List;

/** 앞으로의 방향 후보 하나. steps는 "이렇게 시작해 볼 수 있다"는 선택형 제안(1~3개, 먼저 → 그다음 순서)이다. */
record StoredCoachDirection(String direction, List<Long> recordIds, List<String> steps) {
}
