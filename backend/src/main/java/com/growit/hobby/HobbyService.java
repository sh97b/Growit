package com.growit.hobby;

import com.growit.common.ApiException;
import com.growit.common.ErrorCode;
import com.growit.hobby.dto.HobbyCreateRequest;
import com.growit.hobby.dto.HobbyResponse;
import com.growit.hobby.dto.HobbyUpdateRequest;
import com.growit.record.ActivityRecordRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HobbyService {

	private final HobbyRepository hobbyRepository;
	private final ActivityRecordRepository activityRecordRepository;

	public HobbyService(HobbyRepository hobbyRepository, ActivityRecordRepository activityRecordRepository) {
		this.hobbyRepository = hobbyRepository;
		this.activityRecordRepository = activityRecordRepository;
	}

	public List<HobbyResponse> list(Long userId) {
		return hobbyRepository.findByUserIdOrderByIdAsc(userId).stream()
				.map(this::toResponse)
				.toList();
	}

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
		if (request.goal() != null) {
			hobby.setGoal(request.goal());
		}
		return toResponse(hobby);
	}

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
		return new HobbyResponse(hobby.getId(), hobby.getName(), hobby.getGoal(), recordCount);
	}
}
