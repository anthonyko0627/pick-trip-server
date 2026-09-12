package travel_agency.pick_trip.domain.itinerary.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.itinerary.scheduling.PlannedItinerary;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMode;
import travel_agency.pick_trip.domain.itinerary.scheduling.VariantMetrics;
import travel_agency.pick_trip.domain.region.Region;

/**
 * AI 일정 생성 미리보기 응답.
 * 아직 저장 전 상태이며, 저장은 별도 요청(POST /api/v1/itineraries)으로 처리한다.
 * 각 장소에는 AI가 생성한 배치 이유(reason)와 스케줄러가 확정한 방문 시간이 함께 포함된다.
 *
 * <p>이동수단별 일정안은 {@code variants} 에 담는다. 최상위 {@code title}·{@code days}·{@code adjustments}
 * 는 {@code variants[0]} 의 값을 그대로 복제한 것으로, variants 를 모르는 기존 클라이언트를 위해 남긴다.
 * {@code suggestions}(혼잡 기반 제안)는 확정 시각을 기준으로 만들어지므로 첫 번째 안 기준이며 최상위에 둔다.
 */
public record ItineraryGenerateResponse(
        String title,
        Region region,
        LocalDate travelDate,
        Integer duration,
        List<Day> days,
        List<String> adjustments,
        List<Suggestion> suggestions,
        List<Variant> variants
) {

    /**
     * 이동수단 하나로 만든 일정안.
     *
     * @param label       사용자에게 보여줄 일정안 이름 (예: "자동차 힐링 루트")
     * @param travelMode  이 안이 가정한 이동수단
     * @param title       AI 가 지은 일정 제목 (안 사이에 같다)
     * @param days        이 안의 일차별 일정. 이동수단마다 소요 시간 모델이 달라 순서·배분이 달라진다.
     * @param adjustments AI 원안을 제약으로 보정한 내역
     * @param metrics     안끼리 비교하는 지표. 모든 안이 같은 키 집합을 반환하며,
     *                    산출할 수 없는 지표는 값이 null 이고 사유 코드가 함께 온다.
     */
    public record Variant(
            String label,
            TravelMode travelMode,
            String title,
            List<Day> days,
            List<String> adjustments,
            VariantMetrics metrics
    ) {
    }

    /**
     * 사용자에게 제안만 하는 항목. 서버는 이 제안대로 일정을 재정렬하지 않는다.
     * 사용자가 수락하면 클라이언트가 순서를 바꾼 일정으로 기존 {@code PATCH /api/v1/itineraries/{id}}
     * (수정) 를 호출해 반영한다.
     *
     * @param type               제안 종류. 현재는 {@code CONGESTION_REORDER} 뿐이다.
     * @param message            사용자에게 보여줄 한국어 문장
     * @param dayIndex           제안이 적용되는 일차
     * @param contentId          붐비는 장소
     * @param swapWithContentId  대신 먼저 방문할 장소 (없으면 null)
     */
    public record Suggestion(
            String type,
            String message,
            int dayIndex,
            String contentId,
            String swapWithContentId
    ) {
    }

    /** 혼잡 기반 제안을 덧붙인 사본. 혼잡 조회가 실패하면 빈 리스트 그대로 둔다. */
    public ItineraryGenerateResponse withSuggestions(List<Suggestion> suggestions) {
        return new ItineraryGenerateResponse(
                title, region, travelDate, duration, days, adjustments, suggestions, variants);
    }

    public record Day(
            int dayIndex,
            List<Item> items,
            LocalDate date,
            int totalTravelMinutes,
            double totalTravelKm,
            List<String> dayNotes
    ) {
    }

    public record Item(
            String contentId,
            String title,
            int order,
            String reason,
            // jackson 시간 모듈 기본값은 LocalTime 을 배열/객체로 직렬화하므로, 계약을 "HH:mm" 문자열로 못박는다.
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime,
            List<String> notes,
            // 바구니에 없던 장소를 AI 가 추가 제안한 경우 true (AUGMENT 모드). 사용자가 저장 전 제거할 수 있다.
            boolean addedByAi,
            // 도보 부담이 쌓여 서버가 체력 안배로 자동 삽입한 휴식 스톱이면 true. 삽입 이유는 reason 에 담긴다.
            // AI 추가 제안(addedByAi)과 구분해야 클라이언트가 두 종류를 다르게 안내할 수 있다.
            boolean addedForRest
    ) {
    }

    /**
     * 이동수단별 스케줄링 결과를 미리보기 응답으로 변환한다.
     * 장소 표시명(title)은 AI 응답을 신뢰하지 않고 바구니 스냅샷 매핑을 우선 사용하며,
     * 매핑이 없을 때만 스케줄러가 들고 있던 값으로 대체한다.
     * AUGMENT 로 추가된 장소는 바구니 스냅샷에 없다는 사실 자체로 구분되므로,
     * 스케줄링 결과에 플래그를 심지 않고 여기서 계산한다.
     *
     * @param plannedByMode 요청한 이동수단 순서를 유지하는 맵(LinkedHashMap). 첫 항목이 최상위 필드로도 복제된다.
     * @param metricsByMode 같은 키 집합을 갖는 이동수단별 비교 지표
     */
    public static ItineraryGenerateResponse from(Basket basket,
                                                 Map<TravelMode, PlannedItinerary> plannedByMode,
                                                 Map<TravelMode, VariantMetrics> metricsByMode) {
        Map<String, String> titleByContentId = basket.getItems().stream()
                .collect(Collectors.toMap(
                        BasketItem::getContentId,
                        item -> item.getTitle() == null ? "" : item.getTitle(),
                        (a, b) -> a
                ));

        List<Variant> variants = plannedByMode.entrySet().stream()
                .map(entry -> new Variant(
                        entry.getKey().getLabel(),
                        entry.getKey(),
                        entry.getValue().title(),
                        toDays(entry.getValue(), titleByContentId),
                        entry.getValue().adjustments(),
                        metricsByMode.get(entry.getKey())))
                .toList();

        // 안이 하나도 없는 경우는 요청 정규화(최소 1개) 때문에 나오지 않지만, 최상위 필드가 null 로 새지 않게 막는다.
        Variant primary = variants.isEmpty() ? null : variants.get(0);
        return new ItineraryGenerateResponse(
                primary == null ? null : primary.title(),
                basket.getRegion(),
                basket.getTravelDate(),
                basket.getDuration(),
                primary == null ? List.of() : primary.days(),
                primary == null ? List.of() : primary.adjustments(),
                List.of(),
                variants
        );
    }

    private static List<Day> toDays(PlannedItinerary planned, Map<String, String> titleByContentId) {
        return planned.days().stream()
                .map(day -> new Day(
                        day.dayIndex(),
                        day.stops().stream()
                                .map(stop -> new Item(
                                        stop.contentId(),
                                        titleByContentId.getOrDefault(stop.contentId(), stop.title()),
                                        stop.order(),
                                        stop.reason(),
                                        stop.startTime(),
                                        stop.endTime(),
                                        stop.notes(),
                                        // 휴식 스톱도 바구니 밖 장소지만 AI 가 고른 것이 아니므로 addedByAi 는 아니다.
                                        !stop.autoRest() && !titleByContentId.containsKey(stop.contentId()),
                                        stop.autoRest()
                                ))
                                .toList(),
                        day.date(),
                        day.totalTravelMinutes(),
                        day.totalTravelKm(),
                        day.dayNotes()
                ))
                .toList();
    }
}
