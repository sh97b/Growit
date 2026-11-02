package com.growit.record.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordNeighbor;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordUpdateRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 기록 등록·조회·수정·삭제. {@link com.growit.record.controller.RecordController}에서만 호출된다.
 */
@Service
@Transactional(readOnly = true)
public class RecordService {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;
	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;

	public RecordService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
	}

	/**
	 * 최신순(날짜 내림차순, 같은 날짜면 번호 내림차순) 목록. {@code hobbyId}·{@code from}·{@code to}는 선택 필터다.
	 * {@code size}는 기본 20, 최대 100으로 제한한다. 목록 항목의 이전·다음 기록 링크는 채우지 않는다({@link #get}에서만 채움).
	 */
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

	/**
	 * 기록 상세. 같은 분야 안에서 {@code (recordDate, id)} 순서로 바로 앞·뒤 기록을 함께 찾아 채운다.
	 *
	 * @throws ApiException {@link ErrorCode#NOT_FOUND} 내 기록이 아니거나 없을 때(존재 여부를 숨기려고 404)
	 */
	public RecordResponse get(Long userId, Long id) {
		ActivityRecord record = findOwned(userId, id);
		RecordNeighbor previous = findPrevious(userId, record);
		RecordNeighbor next = findNext(userId, record);
		return toResponse(record, previous, next);
	}

	/**
	 * 기록을 저장한다.
	 *
	 * @throws ApiException {@link ErrorCode#NOT_FOUND} 분야가 내 것이 아닐 때,
	 *                       {@link ErrorCode#VALIDATION_ERROR} 분야가 보관됐거나 날짜가 서버 기준(Asia/Seoul) 오늘보다 미래일 때
	 */
	@Transactional
	public RecordResponse create(Long userId, RecordCreateRequest request) {
		Hobby hobby = findOwnedHobby(userId, request.hobbyId());
		assertNotArchived(hobby);
		assertNotFuture(request.recordDate());

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

	/**
	 * 보낸 칸만 바꾼다. {@code minutes}·{@code difficulty}·{@code feeling}은
	 * {@link org.openapitools.jackson.nullable.JsonNullable}로 "안 보냄(유지)"과 "null로 보냄(비움)"을 구분한다.
	 * {@code content}는 비울 수 없어서 null이거나 빈 값이면 거절한다. 무엇을 고치든 내용이 더는 맞지 않을 수 있으므로
	 * {@code ai_analysis}는 항상 다시 비운다(새로 만들어질 때까지 null).
	 *
	 * @throws ApiException {@link ErrorCode#NOT_FOUND} 기록이나 바꾸려는 분야가 내 것이 아닐 때,
	 *                       {@link ErrorCode#VALIDATION_ERROR} 내용을 비우려 하거나, 연습 시간이 음수이거나,
	 *                       분야가 보관됐거나, 날짜가 서버 기준 오늘보다 미래일 때
	 */
	@Transactional
	public RecordResponse update(Long userId, Long id, RecordUpdateRequest request) {
		ActivityRecord record = findOwned(userId, id);

		if (request.hobbyId() != null) {
			Hobby hobby = findOwnedHobby(userId, request.hobbyId());
			assertNotArchived(hobby);
			record.setHobbyId(request.hobbyId());
		}
		if (request.recordDate() != null) {
			assertNotFuture(request.recordDate());
			record.setRecordDate(request.recordDate());
		}
		if (request.content() != null && request.content().isPresent()) {
			String content = request.content().get();
			if (content == null || content.isBlank()) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "한 일을 입력해 주세요");
			}
			record.setContent(content);
		}
		if (request.minutes() != null && request.minutes().isPresent()) {
			Integer minutes = request.minutes().get();
			if (minutes != null && minutes < 0) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "연습 시간은 0 이상이어야 해요");
			}
			record.setMinutes(minutes);
		}
		if (request.difficulty() != null && request.difficulty().isPresent()) {
			record.setDifficulty(request.difficulty().get());
		}
		if (request.feeling() != null && request.feeling().isPresent()) {
			record.setFeeling(request.feeling().get());
		}
		if (request.isMilestone() != null) {
			record.setMilestone(request.isMilestone());
		}
		record.clearAiAnalysis();

		return toResponse(record);
	}

	/**
	 * 기록을 완전히 삭제한다. 되돌릴 수 없고, 활동 분야와 달리 "보관" 같은 중간 상태가 없다.
	 *
	 * @throws ApiException {@link ErrorCode#NOT_FOUND} 내 기록이 아닐 때
	 */
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

	private void assertNotFuture(LocalDate recordDate) {
		if (recordDate.isAfter(LocalDate.now(SERVER_ZONE))) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "미래 날짜로는 기록할 수 없어요");
		}
	}

	private RecordNeighbor findPrevious(Long userId, ActivityRecord record) {
		List<ActivityRecord> result = activityRecordRepository.findPrevious(userId, record.getHobbyId(),
				record.getRecordDate(), record.getId(), Pageable.ofSize(1));
		return result.isEmpty() ? null : toNeighbor(result.get(0));
	}

	private RecordNeighbor findNext(Long userId, ActivityRecord record) {
		List<ActivityRecord> result = activityRecordRepository.findNext(userId, record.getHobbyId(),
				record.getRecordDate(), record.getId(), Pageable.ofSize(1));
		return result.isEmpty() ? null : toNeighbor(result.get(0));
	}

	private RecordNeighbor toNeighbor(ActivityRecord record) {
		return new RecordNeighbor(record.getId(), record.getRecordDate());
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
		return toResponse(record, null, null);
	}

	private RecordResponse toResponse(ActivityRecord record, RecordNeighbor previous, RecordNeighbor next) {
		return new RecordResponse(
				record.getId(),
				record.getHobbyId(),
				record.getRecordDate(),
				record.getContent(),
				record.getMinutes(),
				record.getDifficulty(),
				record.getFeeling(),
				record.isMilestone(),
				null,
				previous,
				next);
	}
}
