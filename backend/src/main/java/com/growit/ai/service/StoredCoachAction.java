package com.growit.ai.service;

import java.util.List;

record StoredCoachAction(String title, Integer minutes, String reason, List<Long> recordIds) {
}
