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
import travel_agency.pick_trip.domain.region.Region;

/**
 * AI 일정 생성 미리보기 응답.
 * 아직 저장 전 상태이며, 저장은 별도 요청(POST /api/v1/itineraries)으로 처리한다.
 * 각 장소에는 AI가 생성한 배치 이유(reason)와 스케줄러가 확정한 방문 시간이 함께 포함된다.
 */
public record ItineraryGenerateResponse(
        String title,
        Region region,
        LocalDate travelDate,
        Integer duration,
        List<Day> days,
        List<String> adjustments
) {

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
            List<String> notes
    ) {
    }

    /**
     * 스케줄링 결과를 미리보기 응답으로 변환한다.
     * 장소 표시명(title)은 AI 응답을 신뢰하지 않고 바구니 스냅샷 매핑을 우선 사용하며,
     * 매핑이 없을 때만 스케줄러가 들고 있던 값으로 대체한다.
     */
    public static ItineraryGenerateResponse from(Basket basket, PlannedItinerary planned) {
        Map<String, String> titleByContentId = basket.getItems().stream()
                .collect(Collectors.toMap(
                        BasketItem::getContentId,
                        item -> item.getTitle() == null ? "" : item.getTitle(),
                        (a, b) -> a
                ));

        List<Day> days = planned.days().stream()
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
                                        stop.notes()
                                ))
                                .toList(),
                        day.date(),
                        day.totalTravelMinutes(),
                        day.totalTravelKm(),
                        day.dayNotes()
                ))
                .toList();

        return new ItineraryGenerateResponse(
                planned.title(),
                basket.getRegion(),
                basket.getTravelDate(),
                basket.getDuration(),
                days,
                planned.adjustments()
        );
    }
}
