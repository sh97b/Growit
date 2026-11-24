package com.growit.roadmap.dto.response;

/** 방법 블록과, 그 방법에서 뽑은 검색어 제안(사용자가 [추가]를 눌러야 SEARCH 블록이 붙는다). */
public record AiHowtoResponse(BlockResponse block, String suggestedSearchQuery) {
}
