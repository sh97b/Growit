package com.growit.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.hobby.service.HobbyService;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.roadmap.dto.request.RoadmapItemCreateRequest;
import com.growit.roadmap.dto.request.RoadmapItemDoneRequest;
import com.growit.roadmap.dto.request.RoadmapItemUpdateRequest;
import com.growit.roadmap.dto.request.RoadmapStepCreateRequest;
import com.growit.roadmap.dto.request.RoadmapStepUpdateRequest;
import com.growit.roadmap.dto.request.RoadmapUpdateRequest;
import com.growit.roadmap.dto.response.RoadmapResponse;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapItemOrigin;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class RoadmapServiceTest {

	@Autowired
	private RoadmapRepository roadmapRepository;

	@Autowired
	private RoadmapStepRepository stepRepository;

	@Autowired
	private RoadmapItemRepository itemRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	private RoadmapService service() {
		return new RoadmapService(roadmapRepository, stepRepository, itemRepository, hobbyRepository,
				Clock.systemUTC());
	}

	private Hobby hobby(Long userId) {
		return hobbyRepository.save(new Hobby(userId, "기타", "코드 전환 익숙해지기"));
	}

	/** 단계 둘(각각 항목 둘)을 가진 진행 중 로드맵. */
	private Roadmap seed(Long userId, Long hobbyId) {
		Roadmap roadmap = roadmapRepository.save(new Roadmap(userId, hobbyId, "코드 전환 익숙해지기", null, 1));
		for (int s = 1; s <= 2; s++) {
			RoadmapStep step = stepRepository.save(new RoadmapStep(roadmap.getId(), s, "단계 " + s, "약 2주"));
			for (int i = 1; i <= 2; i++) {
				itemRepository.save(new RoadmapItem(step.getId(), i, "항목 " + s + "-" + i, RoadmapItemOrigin.AI));
			}
		}
		return roadmap;
	}

	@Test
	void getActive_returnsNullWhenNone_andRoadmapWhenPresent() {
		Hobby hobby = hobby(1L);
		assertThat(service().getActive(1L, hobby.getId())).isNull();

		seed(1L, hobby.getId());

		RoadmapResponse response = service().getActive(1L, hobby.getId());
		assertThat(response.steps()).hasSize(2);
		assertThat(response.steps().get(0).items()).extracting("text").containsExactly("항목 1-1", "항목 1-2");
		assertThat(response.steps().get(1).position()).isEqualTo(2);
	}

	@Test
	void getActive_otherUsersHobby_isNotFound() {
		Hobby hobby = hobby(1L);

		assertThatThrownBy(() -> service().getActive(2L, hobby.getId())).isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void addStep_andItem_appendAtEnd_withUserOrigin() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());

		RoadmapResponse afterStep = service().addStep(1L, roadmap.getId(),
				new RoadmapStepCreateRequest("새 단계", " "));
		Long newStepId = afterStep.steps().get(2).id();
		RoadmapResponse afterItem = service().addItem(1L, newStepId, new RoadmapItemCreateRequest(" 새 항목 "));

		assertThat(afterItem.steps()).hasSize(3);
		assertThat(afterItem.steps().get(2).periodHint()).isNull();
		assertThat(afterItem.steps().get(2).items().get(0).text()).isEqualTo("새 항목");
		assertThat(afterItem.steps().get(2).items().get(0).source()).isEqualTo("USER");
	}

	@Test
	void addStep_overEightSteps_isRejected() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());
		for (int i = 0; i < 6; i++) {
			service().addStep(1L, roadmap.getId(), new RoadmapStepCreateRequest("단계 " + (i + 3), null));
		}

		assertThatThrownBy(() -> service().addStep(1L, roadmap.getId(), new RoadmapStepCreateRequest("아홉째", null)))
				.isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).getCode())
				.isEqualTo(ErrorCode.VALIDATION_ERROR);
	}

	@Test
	void addItem_overEightItems_isRejected() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());
		Long stepId = service().getActive(1L, hobby.getId()).steps().get(0).id();
		for (int i = 0; i < 6; i++) {
			service().addItem(1L, stepId, new RoadmapItemCreateRequest("추가 " + i));
		}

		assertThatThrownBy(() -> service().addItem(1L, stepId, new RoadmapItemCreateRequest("아홉째")))
				.isInstanceOf(ApiException.class);
		assertThat(roadmap.getId()).isNotNull();
	}

	@Test
	void updateStepAndItem_changeTextOnly() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId());
		RoadmapResponse before = service().getActive(1L, hobby.getId());

		service().updateStep(1L, before.steps().get(0).id(),
				new RoadmapStepUpdateRequest("고친 단계", JsonNullable.of(null), null, null));
		RoadmapResponse after = service().updateItem(1L, before.steps().get(0).items().get(0).id(),
				new RoadmapItemUpdateRequest("고친 항목", null, null));

		assertThat(after.steps().get(0).title()).isEqualTo("고친 단계");
		assertThat(after.steps().get(0).periodHint()).isNull();
		assertThat(after.steps().get(0).items().get(0).text()).isEqualTo("고친 항목");
		assertThat(after.steps().get(1).title()).isEqualTo("단계 2");
	}

	@Test
	void move_swapsNeighbors_andIgnoresEnds() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId());
		RoadmapResponse before = service().getActive(1L, hobby.getId());
		Long firstStep = before.steps().get(0).id();

		RoadmapResponse movedDown = service().updateStep(1L, firstStep,
				new RoadmapStepUpdateRequest(null, null, "DOWN", null));
		assertThat(movedDown.steps()).extracting("title").containsExactly("단계 2", "단계 1");

		RoadmapResponse atEnd = service().updateStep(1L, firstStep, new RoadmapStepUpdateRequest(null, null, "DOWN", null));
		assertThat(atEnd.steps()).extracting("title").containsExactly("단계 2", "단계 1");

		Long item = before.steps().get(0).items().get(0).id();
		RoadmapResponse itemDown = service().updateItem(1L, item, new RoadmapItemUpdateRequest(null, "DOWN", null));
		assertThat(itemDown.steps().get(1).items()).extracting("text").containsExactly("항목 1-2", "항목 1-1");
		RoadmapResponse itemUp = service().updateItem(1L, item, new RoadmapItemUpdateRequest(null, "UP", null));
		assertThat(itemUp.steps().get(1).items()).extracting("text").containsExactly("항목 1-1", "항목 1-2");
	}

	@Test
	void position_dropsTargetAtIndex_forStepsAndItems() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId());
		RoadmapResponse before = service().getActive(1L, hobby.getId());
		Long firstStep = before.steps().get(0).id();

		// 단계 1을 맨 끝(범위 밖 값은 끝으로 보정)으로 끌어 놓는다.
		RoadmapResponse stepMoved = service().updateStep(1L, firstStep,
				new RoadmapStepUpdateRequest(null, null, null, 99));
		assertThat(stepMoved.steps()).extracting("title").containsExactly("단계 2", "단계 1");
		RoadmapResponse stepBack = service().updateStep(1L, firstStep,
				new RoadmapStepUpdateRequest(null, null, null, 0));
		assertThat(stepBack.steps()).extracting("title").containsExactly("단계 1", "단계 2");

		Long item = before.steps().get(0).items().get(0).id();
		RoadmapResponse itemMoved = service().updateItem(1L, item, new RoadmapItemUpdateRequest(null, null, 1));
		assertThat(itemMoved.steps().get(0).items()).extracting("text").containsExactly("항목 1-2", "항목 1-1");
	}

	@Test
	void move_unknownDirection_isRejected() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId());
		Long step = service().getActive(1L, hobby.getId()).steps().get(0).id();

		assertThatThrownBy(() -> service().updateStep(1L, step, new RoadmapStepUpdateRequest(null, null, "LEFT", null)))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void deleteStep_removesItsItems_andDeleteItem_removesOne() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId());
		RoadmapResponse before = service().getActive(1L, hobby.getId());

		RoadmapResponse afterItem = service().deleteItem(1L, before.steps().get(0).items().get(0).id());
		assertThat(afterItem.steps().get(0).items()).hasSize(1);

		RoadmapResponse afterStep = service().deleteStep(1L, before.steps().get(0).id());
		assertThat(afterStep.steps()).hasSize(1);
		assertThat(itemRepository.count()).isEqualTo(2);
	}

	@Test
	void setItemDone_checksAndUnchecks_withDoneAt() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId());
		Long item = service().getActive(1L, hobby.getId()).steps().get(0).items().get(0).id();

		RoadmapResponse checked = service().setItemDone(1L, item, new RoadmapItemDoneRequest(true));
		assertThat(checked.steps().get(0).items().get(0).done()).isTrue();
		assertThat(checked.steps().get(0).items().get(0).doneAt()).isNotNull();

		RoadmapResponse unchecked = service().setItemDone(1L, item, new RoadmapItemDoneRequest(false));
		assertThat(unchecked.steps().get(0).items().get(0).done()).isFalse();
		assertThat(unchecked.steps().get(0).items().get(0).doneAt()).isNull();
	}

	@Test
	void setItemDone_lastItemOfStep_returnsCompletionMessageWithNextStepTitle() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId());
		RoadmapResponse before = service().getActive(1L, hobby.getId());
		Long first = before.steps().get(0).items().get(0).id();
		Long second = before.steps().get(0).items().get(1).id();

		RoadmapResponse afterFirst = service().setItemDone(1L, first, new RoadmapItemDoneRequest(true));
		RoadmapResponse afterSecond = service().setItemDone(1L, second, new RoadmapItemDoneRequest(true));
		RoadmapResponse unchecked = service().setItemDone(1L, second, new RoadmapItemDoneRequest(false));

		assertThat(afterFirst.completedStepMessage()).isNull();
		// "단계 2"는 받침이 있어 "이에요".
		assertThat(afterSecond.completedStepMessage()).isEqualTo("이 단계를 모두 마쳤어요. 다음 단계는 단계 2이에요.");
		assertThat(unchecked.completedStepMessage()).isNull();
		assertThat(service().getActive(1L, hobby.getId()).completedStepMessage()).isNull();
	}

	@Test
	void setItemDone_completionMessage_usesYeoyoWithoutFinalConsonant_andNoNextStepAtEnd() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = roadmapRepository.save(new Roadmap(1L, hobby.getId(), "목표", null, 1));
		RoadmapStep one = stepRepository.save(new RoadmapStep(roadmap.getId(), 1, "처음", null));
		RoadmapStep two = stepRepository.save(new RoadmapStep(roadmap.getId(), 2, "다음 코드", null));
		RoadmapItem a = itemRepository.save(new RoadmapItem(one.getId(), 1, "가", RoadmapItemOrigin.AI));
		RoadmapItem b = itemRepository.save(new RoadmapItem(two.getId(), 1, "나", RoadmapItemOrigin.AI));

		assertThat(service().setItemDone(1L, a.getId(), new RoadmapItemDoneRequest(true)).completedStepMessage())
				.isEqualTo("이 단계를 모두 마쳤어요. 다음 단계는 다음 코드예요.");
		assertThat(service().setItemDone(1L, b.getId(), new RoadmapItemDoneRequest(true)).completedStepMessage())
				.isEqualTo("이 단계를 모두 마쳤어요.");
	}

	@Test
	void contextProvider_returnsFirstUnfinishedStepWithPendingItemsOnly_andScopesToUser() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());
		RoadmapContextProvider provider = new RoadmapContextProvider(roadmapRepository, stepRepository, itemRepository);
		RoadmapResponse before = service().getActive(1L, hobby.getId());
		Long done = before.steps().get(0).items().get(0).id();
		Long pending = before.steps().get(0).items().get(1).id();
		service().setItemDone(1L, done, new RoadmapItemDoneRequest(true));

		var context = provider.currentContext(1L, hobby.getId());
		assertThat(context.stepTitle()).isEqualTo("단계 1");
		assertThat(context.items()).extracting("id").containsExactly(pending);

		assertThat(provider.currentContext(2L, hobby.getId())).isNull();
		assertThat(provider.findPendingItem(1L, pending)).isNotNull();
		assertThat(provider.findPendingItem(1L, done)).isNull();
		assertThat(provider.findPendingItem(2L, pending)).isNull();

		service().archive(1L, roadmap.getId());
		assertThat(provider.currentContext(1L, hobby.getId())).isNull();
		assertThat(provider.findPendingItem(1L, pending)).isNull();
	}

	@Test
	void contextProvider_limitsToSixItems_andSkipsFinishedSteps() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = roadmapRepository.save(new Roadmap(1L, hobby.getId(), "목표", null, 1));
		RoadmapStep finished = stepRepository.save(new RoadmapStep(roadmap.getId(), 1, "끝난 단계", null));
		RoadmapItem doneItem = itemRepository.save(new RoadmapItem(finished.getId(), 1, "했음", RoadmapItemOrigin.AI));
		doneItem.markDone(true, java.time.LocalDateTime.now());
		RoadmapStep open = stepRepository.save(new RoadmapStep(roadmap.getId(), 2, "열린 단계", null));
		for (int i = 1; i <= 8; i++) {
			itemRepository.save(new RoadmapItem(open.getId(), i, "항목 " + i, RoadmapItemOrigin.AI));
		}
		RoadmapContextProvider provider = new RoadmapContextProvider(roadmapRepository, stepRepository, itemRepository);

		var context = provider.currentContext(1L, hobby.getId());

		assertThat(context.stepTitle()).isEqualTo("열린 단계");
		assertThat(context.items()).hasSize(6);
	}

	@Test
	void otherUsersRoadmap_stepAndItem_areNotFound() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());
		RoadmapResponse mine = service().getActive(1L, hobby.getId());
		Long step = mine.steps().get(0).id();
		Long item = mine.steps().get(0).items().get(0).id();

		assertNotFound(() -> service().updateTarget(2L, roadmap.getId(), new RoadmapUpdateRequest(null)));
		assertNotFound(() -> service().archive(2L, roadmap.getId()));
		assertNotFound(() -> service().addStep(2L, roadmap.getId(), new RoadmapStepCreateRequest("x", null)));
		assertNotFound(() -> service().updateStep(2L, step, new RoadmapStepUpdateRequest("x", null, null, null)));
		assertNotFound(() -> service().deleteStep(2L, step));
		assertNotFound(() -> service().addItem(2L, step, new RoadmapItemCreateRequest("x")));
		assertNotFound(() -> service().updateItem(2L, item, new RoadmapItemUpdateRequest("x", null, null)));
		assertNotFound(() -> service().deleteItem(2L, item));
		assertNotFound(() -> service().setItemDone(2L, item, new RoadmapItemDoneRequest(true)));
	}

	@Test
	void archivedRoadmap_cannotBeEdited_andIsNoLongerActive() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());
		Long item = service().getActive(1L, hobby.getId()).steps().get(0).items().get(0).id();

		service().archive(1L, roadmap.getId());

		assertThat(service().getActive(1L, hobby.getId())).isNull();
		assertNotFound(() -> service().setItemDone(1L, item, new RoadmapItemDoneRequest(true)));
		assertNotFound(() -> service().archive(1L, roadmap.getId()));
	}

	@Test
	void updateTarget_setsClearsAndRejectsPastDates() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());
		LocalDate future = LocalDate.now().plusDays(60);

		assertThat(service().updateTarget(1L, roadmap.getId(), new RoadmapUpdateRequest(JsonNullable.of(future)))
				.targetDate()).isEqualTo(future);
		assertThat(service().updateTarget(1L, roadmap.getId(), new RoadmapUpdateRequest(JsonNullable.of(null)))
				.targetDate()).isNull();
		assertThatThrownBy(() -> service().updateTarget(1L, roadmap.getId(),
				new RoadmapUpdateRequest(JsonNullable.of(LocalDate.now().minusDays(3)))))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void deletingHobby_removesItsRoadmaps() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId());
		HobbyService hobbyService = new HobbyService(hobbyRepository, activityRecordRepository, roadmapRepository,
				stepRepository, itemRepository);

		hobbyService.delete(1L, hobby.getId());

		assertThat(roadmapRepository.count()).isZero();
		assertThat(stepRepository.count()).isZero();
		assertThat(itemRepository.count()).isZero();
	}

	private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
		assertThatThrownBy(callable).isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}
}
