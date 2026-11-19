package com.growit.common.text;

import java.util.Locale;
import java.util.Set;

/**
 * AI가 낸 문장이 아무 기록에나 맞는 뻔한 말인지 가볍게 거르는 도구. 문장이 입력(기록·분야 글)에 나온
 * 낱말(2글자 이상)을 하나라도 담고 있어야 구체적이라고 본다.
 */
public final class InputWords {

	/** 어느 기록에나 나오는 흔한 낱말(구체성의 증거가 못 된다). */
	private static final Set<String> COMMON_WORDS = Set.of("연습", "오늘", "어제", "그냥", "조금", "많이", "정말",
			"했다", "했어요", "하기", "너무", "계속", "다시", "처음", "이번", "지난");

	private InputWords() {
	}

	/** 조사 한 글자를 뗀 꼴도 같이 비교한다("코드를"이 입력에 있으면 "코드는"도 맞는 것으로 본다). */
	public static boolean mentions(String text, String corpus) {
		String lowerText = text.toLowerCase(Locale.ROOT);
		for (String rawToken : corpus.split("[^가-힣A-Za-z0-9]+")) {
			String token = rawToken.toLowerCase(Locale.ROOT);
			if (token.length() < 2 || COMMON_WORDS.contains(token)) {
				continue;
			}
			if (lowerText.contains(token)
					|| (token.length() >= 3 && lowerText.contains(token.substring(0, token.length() - 1)))) {
				return true;
			}
		}
		return false;
	}
}
