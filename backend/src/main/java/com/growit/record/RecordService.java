package com.growit.record;

import com.growit.common.ApiException;
import com.growit.common.ErrorCode;
import com.growit.hobby.Hobby;
import com.growit.hobby.HobbyRepository;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordUpdateRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecordService {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;

	public RecordService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
	}

	public RecordListResponse list(Long userId, Long hobbyId, LocalDate from, LocalDate to, Integer page,
			Integer size) {
		int pageNumber = page == null || page < 0 ? 0 : page;
		int pageSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

		Specification<ActivityRecord> spec = buildSpec(userId, hobbyId, from, to);
		PageRequest pageRequest = PageRequest.of(pageNumber, pageSize,
				Sort.by(Sort.Direction.DESC, "recordDate").and(Sort.by(Sort.Direction.DESC, "id")));

		Page<ActivityRecord> result = activityRecordRepository.findAll(spec, pageRequest);
		List<RecordResponse> items = result.getContent().stream().map(this::toResponse).toList();
		return new RecordListResponse(items, result.getTotalElements());
	}

	public RecordResponse get(Long userId, Long id) {
		return toResponse(findOwned(userId, id));
	}

	@Transactional
	public RecordResponse create(Long userId, RecordCreateRequest request) {
		Hobby hobby = findOwnedHobby(userId, request.hobbyId());
		assertNotArchived(hobby);

		ActivityRecord record = new ActivityRecord(
				userId,
				request.hobbyId(),
				request.recordDate(),
				request.content(),
				request.minutes(),
				request.difficulty(),
				request.feeling(),
				request.isMilestone() != null && request.isMilestone());
		activityRecordRepository.save(record);
		return toResponse(record);
	}

	@Transactional
	public RecordResponse update(Long userId, Long id, RecordUpdateRequest request) {
		ActivityRecord record = findOwned(userId, id);

		if (request.hobbyId() != null) {
			Hobby hobby = findOwnedHobby(userId, request.hobbyId());
			assertNotArchived(hobby);
			record.setHobbyId(request.hobbyId());
		}
		if (request.recordDate() != null) {
			record.setRecordDate(request.recordDate());
		}
		if (request.content() != null) {
			if (request.content().isBlank()) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "한 일을 입력해 주세요");
			}
			record.setContent(request.content());
		}
		if (request.minutes() != null) {
			if (request.minutes() < 0) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "연습 시간은 0 이상이어야 해요");
			}
			record.setMinutes(request.minutes());
		}
		if (request.difficulty() != null) {
			record.setDifficulty(request.difficulty());
		}
		if (request.feeling() != null) {
			record.setFeeling(request.feeling());
		}
		if (request.isMilestone() != null) {
			record.setMilestone(request.isMilestone());
		}
		record.clearAiAnalysis();

		return toResponse(record);
	}

	@Transactional
	public void delete(Long userId, Long id) {
		ActivityRecord record = findOwned(userId, id);
		activityRecordRepository.delete(record);
	}

	private ActivityRecord findOwned(Long userId, Long id) {
		return activityRecordRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"));
	}

	private Hobby findOwnedHobby(Long userId, Long hobbyId) {
		return hobbyRepository.findByIdAndUserId(hobbyId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
	}

	private void assertNotArchived(Hobby hobby) {
		if (hobby.isArchived()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "보관된 분야에는 새 기록을 쓸 수 없어요");
		}
	}

	private Specification<ActivityRecord> buildSpec(Long userId, Long hobbyId, LocalDate from, LocalDate to) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("userId"), userId));
			if (hobbyId != null) {
				predicates.add(cb.equal(root.get("hobbyId"), hobbyId));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("recordDate"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("recordDate"), to));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private RecordResponse toResponse(ActivityRecord record) {
		return new RecordResponse(
				record.getId(),
				record.getHobbyId(),
				record.getRecordDate(),
				record.getContent(),
				record.getMinutes(),
				record.getDifficulty(),
				record.getFeeling(),
				record.isMilestone(),
				null);
	}
}
