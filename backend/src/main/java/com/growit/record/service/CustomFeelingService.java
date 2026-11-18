package com.growit.record.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.record.dto.CustomFeelingRequest;
import com.growit.record.dto.CustomFeelingResponse;
import com.growit.record.entity.CustomFeeling;
import com.growit.record.repository.CustomFeelingRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 직접 추가한 감정 목록. 라벨은 AI 프롬프트에도 "자료"로 들어가므로 길이와 문자를 엄격히 제한한다.
 * 남의 감정은 404로 존재를 숨기고, 지워도 이미 쓴 기록에는 라벨과 방향이 복사돼 있어 그대로 보인다.
 */
@Service
@Transactional(readOnly = true)
public class CustomFeelingService {

	static final int MAX_LABEL_LENGTH = 8;
	static final int MAX_PER_USER = 12;
	private static final Pattern ALLOWED = Pattern.compile("^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9 ]+$");
	/** 기본 4개와 예전 화면 문구. 같은 이름을 직접 추가하면 헷갈리므로 막는다. */
	private static final Set<String> BUILT_IN_LABELS = Set.of("뿌듯함", "답답함", "개운함", "담담함", "잘 모르겠음");

	private final CustomFeelingRepository repository;

	public CustomFeelingService(CustomFeelingRepository repository) {
		this.repository = repository;
	}

	public List<CustomFeelingResponse> list(Long userId) {
		return repository.findByUserIdOrderByIdAsc(userId).stream().map(CustomFeelingService::toResponse).toList();
	}

	@Transactional
	public CustomFeelingResponse create(Long userId, CustomFeelingRequest request) {
		String label = request.label() == null ? "" : request.label().trim();
		if (label.isEmpty()) {
			throw invalid("느낌 이름을 입력해 주세요");
		}
		if (label.length() > MAX_LABEL_LENGTH) {
			throw invalid("느낌 이름은 " + MAX_LABEL_LENGTH + "자 이하로 입력해 주세요");
		}
		if (!ALLOWED.matcher(label).matches()) {
			throw invalid("느낌 이름에는 한글, 영문, 숫자, 공백만 쓸 수 있어요");
		}
		String key = label.toLowerCase(Locale.ROOT);
		if (BUILT_IN_LABELS.contains(label)
				|| repository.findByUserIdOrderByIdAsc(userId).stream().anyMatch(own -> own.getLabel().toLowerCase(Locale.ROOT).equals(key))) {
			throw invalid("이미 있는 느낌이에요");
		}
		if (repository.countByUserId(userId) >= MAX_PER_USER) {
			throw invalid("느낌은 " + MAX_PER_USER + "개까지 추가할 수 있어요");
		}
		return toResponse(repository.save(new CustomFeeling(userId, label, request.direction())));
	}

	@Transactional
	public void delete(Long userId, Long id) {
		CustomFeeling feeling = repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "느낌을 찾을 수 없어요"));
		repository.delete(feeling);
	}

	/** 기록에 쓸 때 소유자를 확인해서 가져온다. 남의 것이거나 없으면 404. */
	CustomFeeling findOwned(Long userId, Long id) {
		return repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "느낌을 찾을 수 없어요"));
	}

	private static CustomFeelingResponse toResponse(CustomFeeling feeling) {
		return new CustomFeelingResponse(feeling.getId(), feeling.getLabel(), feeling.getDirection());
	}

	private static ApiException invalid(String message) {
		return new ApiException(ErrorCode.VALIDATION_ERROR, message);
	}
}
