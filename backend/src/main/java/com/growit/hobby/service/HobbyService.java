package com.growit.hobby.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.dto.HobbyCreateRequest;
import com.growit.hobby.dto.HobbyResponse;
import com.growit.hobby.dto.HobbyUpdateRequest;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.repository.ActivityRecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 분야 등록·조회·수정·삭제. {@link com.growit.hobby.controller.HobbyController}에서만 호출된다.
 */
@Service
@Transactional(readOnly = true)
public class HobbyService {

	private final HobbyRepository hobbyRepository;
	private final ActivityRecordRepository activityRecordRepository;

	public HobbyService(HobbyRepository hobbyRepository, ActivityRecordRepository activityRecordRepository) {
		this.hobbyRepository = hobbyRepository;
		this.activityRecordRepository = activityRecordRepository;
	}

	/** 내 분야 목록. {@code includeArchived}가 false면 보관된 분야는 뺀다(기본값). */
	public List<HobbyResponse> list(Long userId, boolean includeArchived) {
		List<Hobby> hobbies = includeArchived
				? hobbyRepository.findByUserIdOrderByIdAsc(userId)
				: hobbyRepository.findByUserIdAndArchivedAtIsNullOrderByIdAsc(userId);
		return hobbies.stream().map(this::toResponse).toList();
	}

	/**
	 * 이름 앞뒤 공백을 지운 뒤 저장한다.
	 *
	 * @throws ApiException {@link ErrorCode#HOBBY_NAME_TAKEN} 같은 사용자 안에 같은 이름이 이미 있을 때
	 */
	@Transactional
	public HobbyResponse create(Long userId, HobbyCreateRequest request) {
		String name = request.name().trim();
		if (hobbyRepository.existsByUserIdAndName(userId, name)) {
			throw new ApiException(ErrorCode.HOBBY_NAME_TAKEN, "이미 사용 중인 분야 이름이에요");
		}
		Hobby hobby = new Hobby(userId, name, request.goal());
		hobbyRepository.save(hobby);
		return toResponse(hobby);
	}

	/**
	 * 보낸 칸만 바꾼다. {@code name}은 보내지 않으면 유지, {@code goal}은
	 * {@link org.openapitools.jackson.nullable.JsonNullable}로 "안 보냄"과 "null로 비움"을 구분한다.
	 *
	 * @throws ApiException {@link ErrorCode#NOT_FOUND} 내 분야가 아닐 때(남의 분야는 존재 여부를 숨기려고 404),
	 *                       {@link ErrorCode#VALIDATION_ERROR} 이름을 빈 값으로 바꾸려 할 때,
	 *                       {@link ErrorCode#HOBBY_NAME_TAKEN} 바꾸려는 이름이 이미 있을 때
	 */
	@Transactional
	public HobbyResponse update(Long userId, Long hobbyId, HobbyUpdateRequest request) {
		Hobby hobby = findOwned(userId, hobbyId);

		if (request.name() != null) {
			String name = request.name().trim();
			if (name.isBlank()) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "분야 이름을 입력해 주세요");
			}
			if (!name.equals(hobby.getName()) && hobbyRepository.existsByUserIdAndName(userId, name)) {
				throw new ApiException(ErrorCode.HOBBY_NAME_TAKEN, "이미 사용 중인 분야 이름이에요");
			}
			hobby.setName(name);
		}
		if (request.goal() != null && request.goal().isPresent()) {
			hobby.setGoal(request.goal().get());
		}
		if (request.archived() != null) {
			if (request.archived()) {
				hobby.archive(LocalDateTime.now());
			} else {
				hobby.unarchive();
			}
		}
		return toResponse(hobby);
	}

	/**
	 * 분야를 완전히 삭제한다(보관과 다름). 기록이 하나라도 있으면 실수로 기록을 잃지 않도록 거절한다.
	 *
	 * @throws ApiException {@link ErrorCode#NOT_FOUND} 내 분야가 아닐 때,
	 *                       {@link ErrorCode#HOBBY_HAS_RECORDS} 이 분야로 쓰인 기록이 하나라도 있을 때
	 */
	@Transactional
	public void delete(Long userId, Long hobbyId) {
		Hobby hobby = findOwned(userId, hobbyId);
		if (activityRecordRepository.existsByHobbyId(hobby.getId())) {
			throw new ApiException(ErrorCode.HOBBY_HAS_RECORDS, "기록이 있는 분야는 삭제할 수 없어요");
		}
		hobbyRepository.delete(hobby);
	}

	private Hobby findOwned(Long userId, Long hobbyId) {
		return hobbyRepository.findByIdAndUserId(hobbyId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
	}

	private HobbyResponse toResponse(Hobby hobby) {
		long recordCount = activityRecordRepository.countByHobbyId(hobby.getId());
		return new HobbyResponse(hobby.getId(), hobby.getName(), hobby.getGoal(), recordCount, hobby.isArchived());
	}
}
