package com.growit.roadmap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.roadmap.dto.response.BlockResponse;
import com.growit.roadmap.entity.BlockKind;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapItemBlock;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemBlockRepository;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로드맵 항목의 블록 조회·추가·수정·삭제·순서 변경. 소유자는 블록→항목→단계→로드맵→사용자로 확인하고 남의 것은
 * 존재를 숨기려고 모두 NOT_FOUND(404)로 답한다. 읽기는 보관된 로드맵에서도 되지만, 바꾸는 요청은 진행 중 로드맵만 받는다.
 */
@Service
@Transactional(readOnly = true)
public class RoadmapBlockService {

	static final int MAX_BLOCKS_PER_ITEM = 12;
	static final int MAX_BLOCKS_PER_ROADMAP = 120;

	private final RoadmapRepository roadmapRepository;
	private final RoadmapStepRepository stepRepository;
	private final RoadmapItemRepository itemRepository;
	private final RoadmapItemBlockRepository blockRepository;
	private final ObjectMapper objectMapper;

	public RoadmapBlockService(RoadmapRepository roadmapRepository, RoadmapStepRepository stepRepository,
			RoadmapItemRepository itemRepository, RoadmapItemBlockRepository blockRepository,
			ObjectMapper objectMapper) {
		this.roadmapRepository = roadmapRepository;
		this.stepRepository = stepRepository;
		this.itemRepository = itemRepository;
		this.blockRepository = blockRepository;
		this.objectMapper = objectMapper;
	}

	public List<BlockResponse> list(Long userId, Long itemId) {
		ownedItem(userId, itemId, false);
		return toResponses(blockRepository.findByItemIdOrderBySortOrderAscIdAsc(itemId));
	}

	@Transactional
	public BlockResponse create(Long userId, Long itemId, String kindName, JsonNode payload) {
		RoadmapItem item = ownedItem(userId, itemId, true);
		BlockKind kind = parseKind(kindName);
		String normalized = BlockPayloads.normalize(kind, payload, objectMapper);
		RoadmapItemBlock saved = append(item, kind, normalized);
		return toResponse(saved, (int) blockRepository.countByItemId(itemId));
	}

	/** 한도(항목당·로드맵당)를 확인하고 맨 뒤에 붙인다. AI 방법 블록도 같은 길로 저장한다. */
	public RoadmapItemBlock append(RoadmapItem item, BlockKind kind, String payloadJson) {
		List<RoadmapItemBlock> existing = blockRepository.findByItemIdOrderBySortOrderAscIdAsc(item.getId());
		if (existing.size() >= MAX_BLOCKS_PER_ITEM) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "블록은 항목마다 최대 " + MAX_BLOCKS_PER_ITEM + "개까지 붙일 수 있어요");
		}
		RoadmapStep step = stepRepository.findById(item.getStepId()).orElseThrow();
		if (blockRepository.countByRoadmapId(step.getRoadmapId()) >= MAX_BLOCKS_PER_ROADMAP) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"블록은 로드맵마다 최대 " + MAX_BLOCKS_PER_ROADMAP + "개까지 붙일 수 있어요");
		}
		int next = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getSortOrder() + 1;
		return blockRepository.save(new RoadmapItemBlock(item.getId(), kind, next, payloadJson));
	}

	@Transactional
	public BlockResponse update(Long userId, Long blockId, JsonNode payload) {
		RoadmapItemBlock block = ownedBlock(userId, blockId, true);
		block.setPayload(BlockPayloads.normalize(block.getKind(), payload, objectMapper));
		List<RoadmapItemBlock> all = blockRepository.findByItemIdOrderBySortOrderAscIdAsc(block.getItemId());
		return toResponse(block, indexOf(all, block.getId()) + 1);
	}

	@Transactional
	public void delete(Long userId, Long blockId) {
		RoadmapItemBlock block = ownedBlock(userId, blockId, true);
		blockRepository.delete(block);
	}

	/** ids는 그 항목의 블록 전체를 원하는 순서로 담아야 한다(빠지거나 남의 id가 섞이면 400). */
	@Transactional
	public List<BlockResponse> reorder(Long userId, Long itemId, List<Long> ids) {
		ownedItem(userId, itemId, true);
		List<RoadmapItemBlock> blocks = blockRepository.findByItemIdOrderBySortOrderAscIdAsc(itemId);
		if (ids == null || ids.size() != blocks.size() || new HashSet<>(ids).size() != ids.size()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "순서를 확인해 주세요");
		}
		Map<Long, RoadmapItemBlock> byId = blocks.stream().collect(Collectors.toMap(RoadmapItemBlock::getId, b -> b));
		List<RoadmapItemBlock> ordered = new ArrayList<>();
		for (Long id : ids) {
			RoadmapItemBlock block = byId.get(id);
			if (block == null) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "순서를 확인해 주세요");
			}
			ordered.add(block);
		}
		for (int i = 0; i < ordered.size(); i++) {
			ordered.get(i).setSortOrder(i + 1);
		}
		return toResponses(ordered);
	}

	// ---- 소유자 확인 ----

	/** 진행 중이어야 하는 요청(active=true)은 보관된 로드맵이면 404다. */
	public RoadmapItem ownedItem(Long userId, Long itemId, boolean activeOnly) {
		RoadmapItem item = itemRepository.findById(itemId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "항목을 찾을 수 없어요"));
		RoadmapStep step = stepRepository.findById(item.getStepId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "항목을 찾을 수 없어요"));
		Roadmap roadmap = roadmapRepository.findByIdAndUserId(step.getRoadmapId(), userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "항목을 찾을 수 없어요"));
		if (activeOnly && !roadmap.isActive()) {
			throw new ApiException(ErrorCode.NOT_FOUND, "항목을 찾을 수 없어요");
		}
		return item;
	}

	private RoadmapItemBlock ownedBlock(Long userId, Long blockId, boolean activeOnly) {
		RoadmapItemBlock block = blockRepository.findById(blockId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "블록을 찾을 수 없어요"));
		try {
			ownedItem(userId, block.getItemId(), activeOnly);
		} catch (ApiException e) {
			throw new ApiException(ErrorCode.NOT_FOUND, "블록을 찾을 수 없어요");
		}
		return block;
	}

	private static BlockKind parseKind(String kindName) {
		try {
			return BlockKind.valueOf(kindName == null ? "" : kindName.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 블록이에요");
		}
	}

	private static int indexOf(List<RoadmapItemBlock> blocks, Long id) {
		for (int i = 0; i < blocks.size(); i++) {
			if (blocks.get(i).getId().equals(id)) {
				return i;
			}
		}
		return 0;
	}

	// ---- 응답 ----

	List<BlockResponse> toResponses(List<RoadmapItemBlock> blocks) {
		List<BlockResponse> responses = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			responses.add(toResponse(blocks.get(i), i + 1));
		}
		return responses;
	}

	public BlockResponse toResponse(RoadmapItemBlock block, int position) {
		JsonNode payload;
		try {
			payload = objectMapper.readTree(block.getPayload());
		} catch (JsonProcessingException e) {
			payload = objectMapper.createObjectNode();
		}
		return new BlockResponse(block.getId(), block.getItemId(), block.getKind().name(), position, payload,
				block.getCreatedAt(), block.getUpdatedAt());
	}
}
