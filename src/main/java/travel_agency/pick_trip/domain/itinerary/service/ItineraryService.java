package travel_agency.pick_trip.domain.itinerary.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.service.ContentService;
import travel_agency.pick_trip.domain.itinerary.dto.request.SaveItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryGenerateResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItinerarySummaryResponse;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryDay;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryItem;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.share.entity.ShareToken;
import travel_agency.pick_trip.domain.share.repository.ShareTokenRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ItineraryException;
import travel_agency.pick_trip.infra.ai.AiItineraryClient;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryRequest;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult;
import travel_agency.pick_trip.infra.ai.dto.AiPlace;

/**
 * AI 일정 생성·저장·조회·수정·재생성 유스케이스.
 * 생성(generate)은 저장 전 미리보기이며, 저장(save)부터 영속화된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryService {

    /** 일정 생성에 필요한 최소 콘텐츠 수. */
    private static final int MIN_CONTENTS = 2;

    private final BasketRepository basketRepository;
    private final ContentService contentService;
    private final AiItineraryClient aiItineraryClient;
    private final ItineraryRepository itineraryRepository;
    private final ShareTokenRepository shareTokenRepository;

    /**
     * 사용자 바구니를 입력으로 AI 일정을 생성한다 (저장 전 미리보기).
     *
     * @throws ItineraryException 입력 조건이 부족하거나 AI 호출이 실패한 경우
     */
    @Transactional(readOnly = true)
    public ItineraryGenerateResponse generate(UUID userId) {
        Basket basket = basketRepository.findByUserId(userId)
                .orElseThrow(() -> new ItineraryException(ErrorCode.ITINERARY_INPUT_INSUFFICIENT));

        validateInput(basket);

        List<AiPlace> places = basket.getItems().stream()
                .map(this::toAiPlace)
                .toList();

        AiItineraryRequest request = new AiItineraryRequest(
                basket.getRegion() == null ? null : basket.getRegion().getName(),
                basket.getTravelDate(),
                basket.getDuration(),
                basket.getCompanions(),
                places
        );

        AiItineraryResult result = aiItineraryClient.generate(request);
        return ItineraryGenerateResponse.from(basket, filterToBasketContents(result, basket));
    }

    /**
     * 생성된(또는 편집된) 일정을 저장한다.
     */
    @Transactional
    public ItineraryResponse save(UUID userId, SaveItineraryRequest request) {
        Itinerary itinerary = Itinerary.builder()
                .userId(userId)
                .title(request.title())
                .region(request.region())
                .travelDate(request.travelDate())
                .duration(request.duration())
                .build();
        itinerary.replaceDays(toDays(request.days()));

        Itinerary saved = itineraryRepository.save(itinerary);
        return ItineraryResponse.from(saved);
    }

    /**
     * 저장된 일정을 조회한다. 본인 소유가 아니면 존재 여부를 노출하지 않도록 NOT_FOUND 로 처리한다.
     */
    @Transactional(readOnly = true)
    public ItineraryResponse getItinerary(UUID userId, UUID itineraryId) {
        return ItineraryResponse.from(findOwned(userId, itineraryId));
    }

    /**
     * 사용자가 저장한 일정 목록을 최근 수정 순으로 조회한다. 저장된 일정이 없으면 빈 리스트를 반환한다.
     */
    @Transactional(readOnly = true)
    public List<ItinerarySummaryResponse> getMyItineraries(UUID userId) {
        return itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(userId).stream()
                .map(ItinerarySummaryResponse::from)
                .toList();
    }

    /**
     * 일정을 삭제한다. {@link travel_agency.pick_trip.domain.share.entity.ShareToken} 은
     * 일정과 FK 없이 {@code itineraryId} 만 들고 있어 일정이 삭제돼도 자동으로 사라지지 않으므로,
     * 활성 공유 토큰이 있으면 명시적으로 비활성화한 뒤 일정을 삭제한다.
     */
    @Transactional
    public void delete(UUID userId, UUID itineraryId) {
        Itinerary itinerary = findOwned(userId, itineraryId);
        shareTokenRepository.findByItineraryIdAndActiveTrue(itineraryId)
                .ifPresent(ShareToken::deactivate);
        itineraryRepository.delete(itinerary);
    }

    /**
     * 일정을 수정한다 (순서 변경·삭제·대체 장소 추가·고정 값을 통째로 반영).
     */
    @Transactional
    public ItineraryResponse modify(UUID userId, UUID itineraryId, SaveItineraryRequest request) {
        Itinerary itinerary = findOwned(userId, itineraryId);
        itinerary.updateTitle(request.title());
        itinerary.replaceDays(toDays(request.days()));
        return ItineraryResponse.from(itinerary);
    }

    /**
     * 저장된 일정을 바구니 기준으로 다시 생성해 덮어쓴다.
     */
    @Transactional
    public ItineraryResponse regenerate(UUID userId, UUID itineraryId) {
        Itinerary itinerary = findOwned(userId, itineraryId);
        ItineraryGenerateResponse generated = generate(userId);
        itinerary.updateTitle(generated.title());
        itinerary.replaceDays(toDaysFromGenerated(generated.days()));
        return ItineraryResponse.from(itinerary);
    }

    // --- 내부 헬퍼 ---

    /**
     * AI 응답에서 바구니에 담기지 않은 contentId 항목을 제거한다.
     * 시스템 프롬프트로 "입력 contentId 만 사용" 을 지시하지만 강제가 아니므로,
     * 모델이 임의의 장소를 섞어 넣더라도 사용자가 담지 않은 장소가 일정에 노출되지 않도록 서버에서 방어한다.
     * 일차 구성·순서·제목은 그대로 두고 미상 항목만 걷어낸다 (빈 일차도 유지).
     */
    private AiItineraryResult filterToBasketContents(AiItineraryResult result, Basket basket) {
        Set<String> knownContentIds = basket.getItems().stream()
                .map(BasketItem::getContentId)
                .collect(Collectors.toSet());

        List<String> removedContentIds = new ArrayList<>();
        List<AiItineraryResult.AiDay> filteredDays = new ArrayList<>();
        for (AiItineraryResult.AiDay day : result.days()) {
            List<AiItineraryResult.AiItem> keptItems = new ArrayList<>();
            for (AiItineraryResult.AiItem item : day.items()) {
                if (knownContentIds.contains(item.contentId())) {
                    keptItems.add(item);
                } else {
                    removedContentIds.add(item.contentId());
                }
            }
            filteredDays.add(new AiItineraryResult.AiDay(day.dayIndex(), keptItems));
        }

        if (!removedContentIds.isEmpty()) {
            log.warn("AI 일정 응답에서 바구니에 없는 장소 {}건을 제외했습니다. contentIds={}",
                    removedContentIds.size(), removedContentIds);
        }
        return new AiItineraryResult(result.title(), filteredDays);
    }

    private void validateInput(Basket basket) {
        if (basket.getItems().size() < MIN_CONTENTS
                || basket.getRegion() == null
                || basket.getDuration() == null) {
            throw new ItineraryException(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
        }
    }

    private Itinerary findOwned(UUID userId, UUID itineraryId) {
        Itinerary itinerary = itineraryRepository.findWithDaysById(itineraryId)
                .orElseThrow(() -> new ItineraryException(ErrorCode.ITINERARY_NOT_FOUND));
        if (!itinerary.isOwnedBy(userId)) {
            throw new ItineraryException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        return itinerary;
    }

    private List<ItineraryDay> toDays(List<SaveItineraryRequest.DayRequest> dayRequests) {
        return dayRequests.stream()
                .map(dayRequest -> {
                    ItineraryDay day = ItineraryDay.builder().dayIndex(dayRequest.dayIndex()).build();
                    dayRequest.items().forEach(item -> day.addItem(ItineraryItem.builder()
                            .contentId(item.contentId())
                            .title(item.title())
                            .orderIndex(item.order())
                            .reason(item.reason())
                            .pinned(item.pinned())
                            .build()));
                    return day;
                })
                .toList();
    }

    private List<ItineraryDay> toDaysFromGenerated(List<ItineraryGenerateResponse.Day> generatedDays) {
        return generatedDays.stream()
                .map(generatedDay -> {
                    ItineraryDay day = ItineraryDay.builder().dayIndex(generatedDay.dayIndex()).build();
                    generatedDay.items().forEach(item -> day.addItem(ItineraryItem.builder()
                            .contentId(item.contentId())
                            .title(item.title())
                            .orderIndex(item.order())
                            .reason(item.reason())
                            .pinned(false)
                            .build()));
                    return day;
                })
                .toList();
    }

    /**
     * 바구니 항목을 AI 입력 장소로 변환한다.
     * 콘텐츠 상세(좌표·운영시간·휴무일·체류시간)를 보강하되, 상세 조회 실패는
     * 일정 생성을 막지 않고 바구니 스냅샷만으로 대체(best-effort)한다.
     */
    private AiPlace toAiPlace(BasketItem item) {
        ContentDetailResponse detail = tryFetchDetail(item.getContentId());
        if (detail == null) {
            return new AiPlace(
                    item.getContentId(),
                    item.getTitle(),
                    item.getContentTypeId(),
                    null, null, null, null, null,
                    item.getPriority().name()
            );
        }
        return new AiPlace(
                item.getContentId(),
                item.getTitle(),
                String.valueOf(detail.contentTypeId()),
                detail.latitude(),
                detail.longitude(),
                detail.useTime(),
                detail.restDate(),
                detail.stayDuration(),
                item.getPriority().name()
        );
    }

    private ContentDetailResponse tryFetchDetail(String contentId) {
        try {
            return contentService.getContentDetail(contentId);
        } catch (Exception e) {
            log.warn("콘텐츠 상세 조회 실패로 스냅샷으로 대체합니다. contentId={}", contentId);
            return null;
        }
    }
}
