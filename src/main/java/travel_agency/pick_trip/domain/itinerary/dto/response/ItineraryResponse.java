package travel_agency.pick_trip.domain.itinerary.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 저장된 일정 상세 응답 (소유자용). 식별자와 고정 여부 등 편집에 필요한 정보를 포함한다.
 * 스케줄러가 만든 notes·adjustments 는 영속화 대상이 아니므로 여기서는 제공하지 않는다.
 */
public record ItineraryResponse(
        UUID itineraryId,
        String title,
        Region region,
        LocalDate travelDate,
        Integer duration,
        LocalDateTime lastModifiedAt,
        List<Day> days
) {

    public record Day(
            UUID dayId,
            int dayIndex,
            List<Item> items,
            Integer totalTravelMinutes,
            BigDecimal totalTravelKm
    ) {
    }

    public record Item(
            UUID itemId,
            String contentId,
            String title,
            int order,
            String reason,
            boolean pinned,
            // jackson 시간 모듈 기본값은 LocalTime 을 배열/객체로 직렬화하므로, 계약을 "HH:mm" 문자열로 못박는다.
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime
    ) {
    }

    public static ItineraryResponse from(Itinerary itinerary) {
        List<Day> days = itinerary.getDays().stream()
                .map(day -> new Day(
                        day.getDayId(),
                        day.getDayIndex(),
                        day.getItems().stream()
                                .map(item -> new Item(
                                        item.getItemId(),
                                        item.getContentId(),
                                        item.getTitle(),
                                        item.getOrderIndex(),
                                        item.getReason(),
                                        item.isPinned(),
                                        item.getVisitStart(),
                                        item.getVisitEnd()
                                ))
                                .toList(),
                        day.getTravelMinutes(),
                        day.getTravelKm()
                ))
                .toList();

        return new ItineraryResponse(
                itinerary.getItineraryId(),
                itinerary.getTitle(),
                itinerary.getRegion(),
                itinerary.getTravelDate(),
                itinerary.getDuration(),
                itinerary.getLastModifiedAt(),
                days
        );
    }
}
