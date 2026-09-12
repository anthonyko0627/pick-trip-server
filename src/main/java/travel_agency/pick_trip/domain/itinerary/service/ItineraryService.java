package travel_agency.pick_trip.domain.itinerary.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.basket.entity.Priority;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.entity.CongestionLevel;
import travel_agency.pick_trip.domain.content.service.CongestionService;
import travel_agency.pick_trip.domain.content.service.ContentService;
import travel_agency.pick_trip.domain.content.service.RoadMatrixResolver;
import travel_agency.pick_trip.domain.itinerary.config.ItineraryCostProperties;
import travel_agency.pick_trip.domain.itinerary.dto.request.GenerateItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.request.GenerateMode;
import travel_agency.pick_trip.domain.itinerary.dto.request.SaveItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryGenerateResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryGenerateResponse.Suggestion;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItinerarySummaryResponse;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryDay;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryItem;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.itinerary.scheduling.ElevationProfile;
import travel_agency.pick_trip.domain.itinerary.scheduling.ItineraryPlanner;
import travel_agency.pick_trip.domain.itinerary.scheduling.OperatingHours;
import travel_agency.pick_trip.domain.itinerary.scheduling.OperatingHoursParser;
import travel_agency.pick_trip.domain.itinerary.scheduling.PlannedItinerary;
import travel_agency.pick_trip.domain.itinerary.scheduling.RestBreaks;
import travel_agency.pick_trip.domain.itinerary.scheduling.ScheduledDay;
import travel_agency.pick_trip.domain.itinerary.scheduling.ScheduledStop;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingContext;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPlace;
import travel_agency.pick_trip.domain.itinerary.scheduling.StayDurationPolicy;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMatrix;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMode;
import travel_agency.pick_trip.domain.itinerary.scheduling.VariantMetrics;
import travel_agency.pick_trip.domain.itinerary.scheduling.VariantMetricsCalculator;
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

    /**
     * AUGMENT 모드에서 프롬프트에 실을 지역 추가 후보 수 상한.
     * 후보 한 건이 프롬프트 두 줄이라 지역 콘텐츠를 전부 실으면 입력 토큰 비용이 그만큼 늘어난다.
     * ponytail: contentId 오름차순 상위 N건만 실으므로 지역 콘텐츠가 이 수를 넘으면 뒤쪽은 후보에서 빠진다.
     * 추천 다양성이 문제로 드러나면 인기·카테고리 기준 샘플링으로 바꾼다.
     */
    private static final int MAX_EXTRA_CANDIDATES = 80;
    /** 혼잡 기반 순서변경 제안의 {@code type} 값. */
    private static final String SUGGESTION_CONGESTION_REORDER = "CONGESTION_REORDER";

    /** 휴식 스톱 후보를 찾을 반경(km). 직전 스톱에서 걸어갈 수 있는 거리라야 쉬어가는 의미가 있다. */
    private static final double REST_SEARCH_RADIUS_KM = 2.0;

    /** 휴식 후보 조회 건수. 이미 일정에 있는 장소·카페 아닌 장소를 걸러내고도 하나는 남도록 여유를 둔다. */
    private static final int REST_SEARCH_SIZE = 10;

    private final BasketRepository basketRepository;
    private final ContentService contentService;
    private final TravelContentRepository travelContentRepository;
    private final AiItineraryClient aiItineraryClient;
    private final ItineraryRepository itineraryRepository;
    private final ShareTokenRepository shareTokenRepository;
    private final CongestionService congestionService;
    private final RoadMatrixResolver roadMatrixResolver;
    private final ItineraryCostProperties costProperties;
    private final ElevationResolver elevationResolver;

    /** 바디 없이 호출한 기존 클라이언트를 위한 기본(STRICT) 생성. */
    @Transactional(readOnly = true)
    public ItineraryGenerateResponse generate(UUID userId) {
        return generate(userId, GenerateItineraryRequest.defaults());
    }

    /**
     * 사용자 바구니를 입력으로 AI 일정을 생성한다 (저장 전 미리보기).
     * {@code AUGMENT} 모드에서는 AI 가 제안한 바구니 밖 장소도 허용하되, 같은 지역에 적재된 콘텐츠로만 한정한다.
     *
     * @throws ItineraryException 입력 조건이 부족하거나 AI 호출이 실패한 경우
     */
    @Transactional(readOnly = true)
    public ItineraryGenerateResponse generate(UUID userId, GenerateItineraryRequest generateRequest) {
        Basket basket = basketRepository.findByUserId(userId)
                .orElseThrow(() -> new ItineraryException(ErrorCode.ITINERARY_INPUT_INSUFFICIENT));

        validateInput(basket);
        validateStartContent(basket, generateRequest.startContentId());

        boolean augment = generateRequest.mode() == GenerateMode.AUGMENT;
        List<AiPlace> places = new ArrayList<>(basket.getItems().stream()
                .map(this::toAiPlace)
                .toList());

        AiItineraryRequest request = new AiItineraryRequest(
                basket.getRegion() == null ? null : basket.getRegion().getName(),
                basket.getTravelDate(),
                basket.getDuration(),
                basket.getCompanions().stream().map(TravelCondition::getLabel).toList(),
                List.copyOf(places),
                // 후보를 주지 않으면 AI 는 실제 contentId 를 알 수 없어 추가 제안이 전부 화이트리스트에서 걸린다.
                augment ? findExtraCandidates(basket) : List.of()
        );

        AiItineraryResult result = aiItineraryClient.generate(request);
        if (augment) {
            // 화이트리스트를 "바구니 U 지역 내 유효 콘텐츠" 로 넓힌다. 스케줄링도 같은 목록을 쓰므로
            // 추가된 장소에도 시각·이동 시간이 동일하게 배정된다.
            places.addAll(resolveExtraPlaces(result, basket));
        }

        // 화이트리스트 필터와 이유 문구 정제를 먼저 통과시킨 뒤 스케줄링한다.
        // 스케줄러가 AI 원문을 그대로 받으면 걸러졌어야 할 장소에 시각까지 배정된다.
        AiItineraryResult cleaned = sanitizeReasons(filterToKnownContents(result, places));
        PlannedVariants planned = planByMode(cleaned, places, basket, generateRequest);
        ItineraryGenerateResponse response = ItineraryGenerateResponse.from(
                basket, planned.plannedByMode(), planned.metricsByMode());
        return response.withSuggestions(buildCongestionSuggestions(response));
    }

    /**
     * 생성된(또는 편집된) 일정을 저장한다.
     * 방문 시각은 클라이언트가 보낸 값을 그대로 저장한다. 사용자가 미리보기에서 조정한 시각을 서버가 다시 덮어쓰면 편집이 무의미해지기 때문이다.
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

    // --- 혼잡 기반 순서변경 제안 (#73) ---

    /**
     * 확정된 방문 시각 기준으로 붐비는 장소를 찾아 순서변경을 제안한다.
     * <b>실제 재정렬은 하지 않는다.</b> 사용자가 제안을 수락하면 클라이언트가 순서를 바꾼 일정으로
     * 기존 {@code PATCH /api/v1/itineraries/{id}} ({@link #modify}) 를 호출해 반영한다.
     *
     * <p>혼잡 조회가 실패해도 일정 생성 자체는 성공해야 하므로 예외를 삼키고 빈 리스트를 반환한다.
     */
    private List<Suggestion> buildCongestionSuggestions(ItineraryGenerateResponse response) {
        List<String> contentIds = response.days().stream()
                .flatMap(day -> day.items().stream())
                .filter(item -> item.startTime() != null)
                .map(ItineraryGenerateResponse.Item::contentId)
                .distinct()
                .toList();
        if (contentIds.isEmpty()) {
            return List.of();
        }

        Map<String, Map<Integer, CongestionLevel>> levels;
        try {
            levels = congestionService.findLevels(contentIds);
        } catch (RuntimeException e) {
            log.warn("혼잡 조회에 실패해 순서변경 제안 없이 일정을 반환합니다: {}", e.getMessage());
            return List.of();
        }
        if (levels.isEmpty()) {
            return List.of();
        }

        List<Suggestion> suggestions = new ArrayList<>();
        for (ItineraryGenerateResponse.Day day : response.days()) {
            suggestions.addAll(suggestionsForDay(day, levels));
        }
        return List.copyOf(suggestions);
    }

    /**
     * 한 일차 안에서, 방문 시작 시각의 혼잡이 HIGH 인 장소마다 같은 시간대 혼잡이 더 낮은
     * 뒤쪽 장소를 찾아 둘을 바꾸자고 제안한다. 뒤쪽에 더 나은 후보가 없으면 제안하지 않는다.
     */
    private List<Suggestion> suggestionsForDay(
            ItineraryGenerateResponse.Day day, Map<String, Map<Integer, CongestionLevel>> levels) {
        List<ItineraryGenerateResponse.Item> items = day.items();
        List<Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItineraryGenerateResponse.Item crowded = items.get(i);
            if (crowded.startTime() == null) {
                continue;
            }
            int hourSlot = crowded.startTime().getHour();
            if (levelOf(levels, crowded.contentId(), hourSlot) != CongestionLevel.HIGH) {
                continue;
            }
            for (int j = i + 1; j < items.size(); j++) {
                ItineraryGenerateResponse.Item candidate = items.get(j);
                CongestionLevel candidateLevel = levelOf(levels, candidate.contentId(), hourSlot);
                if (candidateLevel != null && candidateLevel != CongestionLevel.HIGH) {
                    suggestions.add(new Suggestion(
                            SUGGESTION_CONGESTION_REORDER,
                            "%d시쯤 %s은(는) 너무 붐빕니다. %s을(를) 먼저 방문하도록 순서를 바꿀까요?"
                                    .formatted(hourSlot, crowded.title(), candidate.title()),
                            day.dayIndex(),
                            crowded.contentId(),
                            candidate.contentId()));
                    break;
                }
            }
        }
        return suggestions;
    }

    /** 스냅샷이 없는 콘텐츠·시간대는 null(모름)로 다룬다. */
    private CongestionLevel levelOf(
            Map<String, Map<Integer, CongestionLevel>> levels, String contentId, int hourSlot) {
        return levels.getOrDefault(contentId, Map.of()).get(hourSlot);
    }

    // --- 내부 헬퍼 ---

    /**
     * AI 응답에서 화이트리스트({@code allowed})에 없는 contentId 항목을 제거한다.
     * 시스템 프롬프트로 사용할 contentId 범위를 지시하지만 강제가 아니므로,
     * 모델이 지어낸 장소가 일정에 노출되지 않도록 서버에서 방어한다.
     * 화이트리스트는 STRICT 면 바구니, AUGMENT 면 바구니에 지역 내 유효 콘텐츠를 더한 집합이다.
     * 일차 구성·순서·제목은 그대로 두고 미상 항목만 걷어낸다 (빈 일차도 유지).
     */
    private AiItineraryResult filterToKnownContents(AiItineraryResult result, Collection<AiPlace> allowed) {
        Set<String> knownContentIds = allowed.stream()
                .map(AiPlace::contentId)
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
            log.warn("AI 일정 응답에서 허용되지 않은 장소 {}건을 제외했습니다. contentIds={}",
                    removedContentIds.size(), removedContentIds);
        }
        return new AiItineraryResult(result.title(), filteredDays);
    }

    /**
     * AI 응답의 배치 이유(reason)에서 사용자가 이해할 수 없는 내부 값
     * (contentId 표기·괄호 안 숫자 ID·동행/우선순위 enum 코드)을 제거·치환한다.
     * 시스템 프롬프트로 금지를 지시하지만 강제가 아니므로 서버에서 방어한다.
     */
    private AiItineraryResult sanitizeReasons(AiItineraryResult result) {
        int changedCount = 0;
        List<AiItineraryResult.AiDay> days = new ArrayList<>();
        for (AiItineraryResult.AiDay day : result.days()) {
            List<AiItineraryResult.AiItem> items = new ArrayList<>();
            for (AiItineraryResult.AiItem item : day.items()) {
                String sanitized = ReasonSanitizer.sanitize(item.reason());
                if (!Objects.equals(sanitized, item.reason())) {
                    changedCount++;
                }
                items.add(new AiItineraryResult.AiItem(item.contentId(), item.order(), sanitized));
            }
            days.add(new AiItineraryResult.AiDay(day.dayIndex(), items));
        }

        if (changedCount > 0) {
            log.warn("AI 일정 응답의 배치 이유 {}건에서 내부 값을 제거했습니다.", changedCount);
        }
        return new AiItineraryResult(result.title(), days);
    }

    private void validateInput(Basket basket) {
        if (basket.getItems().size() < MIN_CONTENTS
                || basket.getRegion() == null
                || basket.getDuration() == null) {
            throw new ItineraryException(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
        }
    }

    /**
     * 시작 지점은 바구니에 담긴 장소여야 한다. AI 추가 제안(AUGMENT) 장소는 응답을 받기 전이라 알 수 없고,
     * 사용자가 고를 수 있는 대상도 바구니뿐이다.
     */
    private void validateStartContent(Basket basket, String startContentId) {
        if (startContentId == null) {
            return;
        }
        if (!basketContentIds(basket).contains(startContentId)) {
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
                    ItineraryDay day = ItineraryDay.builder()
                            .dayIndex(dayRequest.dayIndex())
                            .travelMinutes(dayRequest.totalTravelMinutes())
                            .travelKm(dayRequest.totalTravelKm())
                            .build();
                    dayRequest.items().forEach(item -> day.addItem(ItineraryItem.builder()
                            .contentId(item.contentId())
                            .title(item.title())
                            .orderIndex(item.order())
                            .reason(ReasonSanitizer.sanitize(item.reason()))
                            .pinned(item.pinned())
                            .visitStart(item.startTime())
                            .visitEnd(item.endTime())
                            .build()));
                    return day;
                })
                .toList();
    }

    private List<ItineraryDay> toDaysFromGenerated(List<ItineraryGenerateResponse.Day> generatedDays) {
        return generatedDays.stream()
                .map(generatedDay -> {
                    ItineraryDay day = ItineraryDay.builder()
                            .dayIndex(generatedDay.dayIndex())
                            .travelMinutes(generatedDay.totalTravelMinutes())
                            .travelKm(BigDecimal.valueOf(generatedDay.totalTravelKm())
                                    .setScale(2, RoundingMode.HALF_UP))
                            .build();
                    generatedDay.items().forEach(item -> day.addItem(ItineraryItem.builder()
                            .contentId(item.contentId())
                            .title(item.title())
                            .orderIndex(item.order())
                            .reason(ReasonSanitizer.sanitize(item.reason()))
                            .pinned(false)
                            .visitStart(item.startTime())
                            .visitEnd(item.endTime())
                            .build()));
                    return day;
                })
                .toList();
    }

    /**
     * 요청한 이동수단마다 일정안을 하나씩 만든다. AI 호출은 이미 끝났고, 같은 AI 결과·같은 장소 집합으로
     * 스케줄링만 다시 돈다. 이동시간 모델이 달라 일차 배분과 방문 순서가 안마다 달라진다.
     *
     * <p>도로 거리 행렬은 자동차 안에만 쓰므로, 자동차 안이 있을 때 한 번만 조회해 재사용한다.
     * 대중교통 안은 조회하지 않는다(자동차 경로라 무의미하고 API 사용량만 쓴다).
     *
     * <p>고도는 안마다 장소 집합이 같으므로 루프 밖에서 한 번만 조회해 모든 안이 공유한다.
     */
    private PlannedVariants planByMode(AiItineraryResult cleaned, List<AiPlace> places,
                                       Basket basket, GenerateItineraryRequest generateRequest) {
        // AI 입력을 만들 때 이미 콘텐츠 상세를 보강해뒀으므로, 안마다 다시 만들지 않고 한 번만 변환해 재사용한다.
        Map<String, SchedulingPlace> placesById = places.stream()
                .collect(Collectors.toMap(
                        AiPlace::contentId,
                        this::toSchedulingPlace,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        // 장소 집합은 안마다 같으므로 고도는 variant 루프 밖에서 한 번만 조회해 모든 안이 공유한다.
        List<SchedulingPlace> schedulingPlaces = List.copyOf(placesById.values());
        ElevationProfile elevations = elevationResolver.resolve(schedulingPlaces);

        TravelMatrix roadMatrix = null;
        Map<TravelMode, PlannedItinerary> plannedByMode = new LinkedHashMap<>();
        Map<TravelMode, VariantMetrics> metricsByMode = new LinkedHashMap<>();
        for (TravelMode mode : generateRequest.travelModes()) {
            if (mode == TravelMode.CAR && roadMatrix == null) {
                roadMatrix = resolveRoadMatrix(schedulingPlaces);
            }
            SchedulingContext context = new SchedulingContext(
                    mode,
                    mode == TravelMode.CAR ? roadMatrix : TravelMatrix.empty(),
                    generateRequest.startContentId(),
                    elevations);
            // 자동 삽입된 휴식 스톱을 지표가 되짚을 수 있어야 하는데, 그 장소를 공용 맵에 넣으면
            // 다른 안의 스케줄링에까지 샌다. 안마다 사본을 써서 삽입분을 그 안에만 남긴다.
            Map<String, SchedulingPlace> variantPlaces = new LinkedHashMap<>(placesById);
            PlannedItinerary planned = toPlanned(cleaned, variantPlaces, basket.getTravelDate(), context);
            plannedByMode.put(mode, planned);
            // 지표는 스케줄링과 같은 컨텍스트로 계산해야 도보 판정·거리 기준이 일정과 어긋나지 않는다.
            // 휴식 스톱까지 반영된 결과를 넘겨야 장소 수·도보 시간이 실제 응답과 일치한다.
            metricsByMode.put(mode, VariantMetricsCalculator.calculate(
                    planned, context, variantPlaces,
                    costProperties.carCostPerKmWon(), costProperties.transitBaseFareWon()));
        }
        return new PlannedVariants(plannedByMode, metricsByMode);
    }

    /** 이동수단별 일정안과 그 지표. 두 맵은 같은 키(요청 순서)를 갖는다. */
    private record PlannedVariants(
            Map<TravelMode, PlannedItinerary> plannedByMode,
            Map<TravelMode, VariantMetrics> metricsByMode
    ) {
    }

    /** 도로 거리 조회가 실패해도 일정 생성은 성공해야 하므로, 빈 행렬(직선거리 폴백)로 진행한다. */
    private TravelMatrix resolveRoadMatrix(List<SchedulingPlace> places) {
        try {
            return roadMatrixResolver.resolve(places);
        } catch (Exception e) {
            log.warn("도로 거리 행렬 조회에 실패해 직선거리로 진행합니다: {}", e.getMessage());
            return TravelMatrix.empty();
        }
    }

    /**
     * AI 원안을 영업시간·이동시간 제약이 반영된 확정 일정으로 바꾸고, 도보 안이면 휴식 스톱까지 끼워 넣는다.
     * 스케줄링이 깨져 일정 생성 전체가 실패하는 편이 사용자에게 더 손해이므로, 실패 시 AI 순서를 그대로 쓰는 형태로 폴백한다.
     */
    private PlannedItinerary toPlanned(AiItineraryResult result, Map<String, SchedulingPlace> placesById,
                                       LocalDate travelDate, SchedulingContext context) {
        try {
            PlannedItinerary planned = ItineraryPlanner.plan(
                    result.title(), toDayContentIds(result), placesById, toReasonByContentId(result), travelDate,
                    context);
            return withRestStops(planned, placesById, context);
        } catch (Exception e) {
            log.warn("일정 스케줄링에 실패해 AI 순서를 그대로 사용합니다.", e);
            return unscheduled(result, placesById.keySet());
        }
    }

    // --- 체력 안배 휴식 스톱 자동 삽입 (#72) ---

    /**
     * 도보 부담이 쌓인 지점에 근처 카페를 휴식 스톱으로 끼워 넣는다.
     * 자동차 안은 경사·도보 부담이 없어 대상이 아니다(이슈도 도보 일정을 대상으로 한다).
     *
     * <p>삽입 지점 계산은 순수 코드({@link RestBreaks})가 하고, 실제 콘텐츠 조회만 여기서 한다.
     */
    private PlannedItinerary withRestStops(PlannedItinerary planned, Map<String, SchedulingPlace> placesById,
                                           SchedulingContext context) {
        if (context.travelMode() != TravelMode.TRANSIT) {
            return planned;
        }
        // 이미 일정에 들어 있는 장소는 후보에서 뺀다. 같은 장소를 두 번 방문하는 일정이 되기 때문이다.
        Set<String> used = planned.days().stream()
                .flatMap(day -> day.stops().stream())
                .map(ScheduledStop::contentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ScheduledDay> days = new ArrayList<>(planned.days().size());
        for (ScheduledDay day : planned.days()) {
            days.add(withRestStops(day, placesById, context, used));
        }
        return new PlannedItinerary(planned.title(), days, planned.adjustments());
    }

    private ScheduledDay withRestStops(ScheduledDay day, Map<String, SchedulingPlace> placesById,
                                       SchedulingContext context, Set<String> used) {
        List<SchedulingPlace> ordered = day.stops().stream()
                .map(stop -> placesById.get(stop.contentId()))
                .toList();
        // 스톱 하나라도 장소를 되짚지 못하면(폴백 경로 등) 시각을 다시 매길 수 없으므로 건드리지 않는다.
        if (ordered.contains(null)) {
            return day;
        }

        List<RestBreaks.Point> points = RestBreaks.findPoints(ordered, context);
        if (points.isEmpty()) {
            return day;
        }

        List<RestBreaks.Insertion> insertions = new ArrayList<>(points.size());
        for (RestBreaks.Point point : points) {
            SchedulingPlace place = findRestPlace(point.afterContentId(), used);
            if (place != null) {
                used.add(place.contentId());
                // 지표 계산이 이 스톱을 낀 구간까지 재려면 장소를 되짚을 수 있어야 한다.
                placesById.put(place.contentId(), place);
                insertions.add(new RestBreaks.Insertion(point.afterContentId(), place, point.reason()));
            }
        }
        return RestBreaks.insert(day, ordered, context, insertions);
    }

    /**
     * 직전 스톱 근처의 카페(음식 분류)를 하나 고른다.
     * 후보가 없거나 조회가 실패하면 예외를 던지지 않고 null 을 돌려준다 — 휴식은 보조 기능이라
     * 삽입하지 않고 일정을 그대로 내보내는 편이 낫다.
     *
     * <p>ponytail: 삽입 지점마다 근처 조회를 한 번씩 한다(내부적으로 길찾기도 탄다).
     * 하루에 휴식이 한두 번이라 지금은 문제가 없고, 호출량이 걸리면 그날 스톱 전체의 근처 카페를
     * 한 번에 모아 오는 형태로 올린다.
     */
    private SchedulingPlace findRestPlace(String afterContentId, Set<String> used) {
        try {
            return contentService.getNearbyContents(afterContentId, REST_SEARCH_RADIUS_KM, REST_SEARCH_SIZE)
                    .items().stream()
                    .filter(item -> item.contentId() != null && !used.contains(item.contentId()))
                    .filter(ItineraryService::isRestCandidate)
                    .findFirst()
                    .map(ItineraryService::toRestPlace)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[휴식] 근처 카페 조회에 실패해 휴식 스톱 없이 진행합니다. contentId={}", afterContentId);
            return null;
        }
    }

    /** 카페·식당 판정은 기존 {@link ContentCategory} 분류를 그대로 쓴다. 별도 분류 체계를 두지 않는다. */
    private static boolean isRestCandidate(NearbyContentItem item) {
        // 근처 조회는 좌표를 모르면 0,0 으로 내려준다. 그대로 쓰면 이동 시간이 엉뚱하게 잡힌다.
        if (item.latitude() == 0.0 && item.longitude() == 0.0) {
            return false;
        }
        ContentCategory category = item.category() != null
                ? item.category()
                : ContentCategory.fromContentTypeId(item.contentTypeId());
        return category == ContentCategory.FOOD;
    }

    /** 근처 조회 응답에는 운영시간이 없어 미상으로 둔다(운영시간 위반 판정에서 빠진다). */
    private static SchedulingPlace toRestPlace(NearbyContentItem item) {
        return new SchedulingPlace(
                item.contentId(),
                item.title(),
                parseContentTypeId(item.contentTypeId()),
                item.latitude(),
                item.longitude(),
                OperatingHours.unknown(),
                RestBreaks.REST_STAY_MINUTES,
                false);
    }

    private SchedulingPlace toSchedulingPlace(AiPlace place) {
        Integer contentTypeId = parseContentTypeId(place.category());
        return new SchedulingPlace(
                place.contentId(),
                place.title(),
                contentTypeId,
                place.latitude(),
                place.longitude(),
                OperatingHoursParser.parse(place.useTime(), place.restDate()),
                StayDurationPolicy.stayMinutes(contentTypeId),
                // AiPlace 는 우선순위를 한국어 라벨로만 들고 있다(AUGMENT 로 추가된 장소는 null 이라 false).
                Priority.MUST_VISIT.getLabel().equals(place.priority())
        );
    }

    /** category 는 TourAPI contentTypeId 문자열이지만 바구니 스냅샷 값이 섞일 수 있어 형식을 보장하지 못한다. */
    private static Integer parseContentTypeId(String category) {
        if (category == null) {
            return null;
        }
        try {
            return Integer.valueOf(category.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 스케줄러는 일차를 리스트 인덱스로만 해석하므로 dayIndex·order 정렬을 여기서 끝낸다. */
    private List<List<String>> toDayContentIds(AiItineraryResult result) {
        return aiDays(result).stream()
                .sorted(Comparator.comparingInt(AiItineraryResult.AiDay::dayIndex))
                .map(day -> aiItems(day).stream()
                        .sorted(Comparator.comparingInt(AiItineraryResult.AiItem::order))
                        .map(AiItineraryResult.AiItem::contentId)
                        .toList())
                .toList();
    }

    private Map<String, String> toReasonByContentId(AiItineraryResult result) {
        // reason 이 null 일 수 있어 null 값을 허용하지 않는 Collectors.toMap 대신 putIfAbsent 를 쓴다.
        Map<String, String> reasons = new LinkedHashMap<>();
        aiDays(result).forEach(day ->
                aiItems(day).forEach(item -> reasons.putIfAbsent(item.contentId(), item.reason())));
        return reasons;
    }

    /** 스케줄링 폴백. 바구니에 없는 장소만 걷어내고, 시각·이동 요약 없이 AI 순서를 그대로 유지한다. */
    private PlannedItinerary unscheduled(AiItineraryResult result, Set<String> allowed) {
        List<ScheduledDay> days = new ArrayList<>();
        for (AiItineraryResult.AiDay aiDay : aiDays(result)) {
            List<ScheduledStop> stops = new ArrayList<>();
            for (AiItineraryResult.AiItem item : aiItems(aiDay)) {
                if (allowed.contains(item.contentId())) {
                    stops.add(new ScheduledStop(
                            item.contentId(), null, stops.size() + 1, item.reason(), null, null, List.of()));
                }
            }
            days.add(new ScheduledDay(aiDay.dayIndex(), null, stops, 0, 0.0, List.of()));
        }
        return new PlannedItinerary(result.title(), days, List.of());
    }

    private List<AiItineraryResult.AiDay> aiDays(AiItineraryResult result) {
        return result.days() == null ? List.of() : result.days();
    }

    private List<AiItineraryResult.AiItem> aiItems(AiItineraryResult.AiDay day) {
        return day.items() == null ? List.of() : day.items();
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
                    item.getPriority().getLabel()
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
                item.getPriority().getLabel()
        );
    }

    /**
     * AUGMENT 모드에서 AI 에게 제시할 같은 지역의 추가 후보를 조회한다.
     * 프롬프트에는 id·이름·분류만 있으면 되므로 상세는 조회하지 않고 repository 한 번으로 끝낸다.
     * 조회가 실패하거나 후보가 없으면 후보 없이 진행한다 (그 경우 AUGMENT 는 STRICT 로 수렴한다).
     */
    private List<AiPlace> findExtraCandidates(Basket basket) {
        if (basket.getRegion() == null) {
            return List.of();
        }
        try {
            return travelContentRepository.findRegionCandidates(
                            basket.getRegion(),
                            DataStatus.ACTIVE,
                            basketContentIds(basket),
                            PageRequest.of(0, MAX_EXTRA_CANDIDATES))
                    .stream()
                    .map(candidate -> new AiPlace(
                            candidate.contentId(), candidate.title(), candidate.contentTypeId(),
                            null, null, null, null, null, null))
                    .toList();
        } catch (Exception e) {
            log.warn("AUGMENT 추가 후보 조회에 실패해 후보 없이 진행합니다.", e);
            return List.of();
        }
    }

    private Set<String> basketContentIds(Basket basket) {
        return basket.getItems().stream()
                .map(BasketItem::getContentId)
                .collect(Collectors.toSet());
    }

    /**
     * AUGMENT 모드에서 AI 가 제안한 바구니 밖 contentId 중, 같은 지역에 ACTIVE 로 적재된 것만 추가 장소로 인정한다.
     * AI 가 지어낸 id 는 DB 에 없으므로 여기서 걸러진다. 상세 조회 실패는 일정 생성을 막지 않고
     * 해당 장소만 제외한다(best-effort).
     *
     * <p>지역 콘텐츠 전체를 후보로 올리지 않고, AI 응답에 실제로 등장한 id 만 조회해 확인한다.
     */
    private List<AiPlace> resolveExtraPlaces(AiItineraryResult result, Basket basket) {
        Set<String> basketContentIds = basketContentIds(basket);
        // 같은 장소를 여러 번 제안해도 한 번만 조회하도록 중복을 없애고, 로그 순서를 위해 삽입 순서를 유지한다.
        Set<String> candidates = aiDays(result).stream()
                .flatMap(day -> aiItems(day).stream())
                .map(AiItineraryResult.AiItem::contentId)
                .filter(contentId -> contentId != null && !basketContentIds.contains(contentId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (candidates.isEmpty() || basket.getRegion() == null) {
            return List.of();
        }

        // 프롬프트로 후보를 제시했더라도 모델이 목록 밖 id 를 섞을 수 있으므로 여기서 다시 검증한다.
        List<String> allowedIds = travelContentRepository.findIdsByRegion(
                candidates, basket.getRegion(), DataStatus.ACTIVE);

        List<AiPlace> extras = new ArrayList<>();
        for (String contentId : allowedIds) {
            ContentDetailResponse detail = tryFetchDetail(contentId);
            if (detail != null) {
                extras.add(toAiPlace(detail));
            }
        }
        log.info("AUGMENT 모드에서 AI 추가 제안 {}건 중 {}건을 지역 내 콘텐츠로 채택했습니다.",
                candidates.size(), extras.size());
        return extras;
    }

    /** AI 가 추가 제안한 장소. 바구니 스냅샷이 없으므로 우선순위 라벨도 없다. */
    private AiPlace toAiPlace(ContentDetailResponse detail) {
        return new AiPlace(
                detail.contentId(),
                detail.title(),
                String.valueOf(detail.contentTypeId()),
                detail.latitude(),
                detail.longitude(),
                detail.useTime(),
                detail.restDate(),
                detail.stayDuration(),
                null
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
