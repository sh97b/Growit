package com.growit.roadmap.dto.request;

import java.util.List;

/** 그 항목의 블록 id 전체를 원하는 순서로 담는다. */
public record BlockOrderRequest(List<Long> ids) {
}
