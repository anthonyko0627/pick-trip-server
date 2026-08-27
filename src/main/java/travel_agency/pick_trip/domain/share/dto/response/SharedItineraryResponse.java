package travel_agency.pick_trip.domain.share.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 공유 토큰으로 조회하는 공개 일정 응답 (읽기 전용).
 * 소유자·내부 식별자·고정 여부 등 편집용 정보는 노출하지 않는다.
 */
public record SharedItineraryResponse(
        String title,
        Region region,
        LocalDate travelDate,
        Integer duration,
        List<Day> days
) {

    public record Day(
            int dayIndex,
            List<Item> items,
            Integer totalTravelMinutes,
            BigDecimal totalTravelKm
    ) {
    }

    public record Item(
            String contentId,
            String title,
            int order,
            String reason,
            // jackson 시간 모듈 기본값은 LocalTime 을 배열/객체로 직렬화하므로, 계약을 "HH:mm" 문자열로 못박는다.
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime
    ) {
    }

    public static SharedItineraryResponse from(Itinerary itinerary) {
        List<Day> days = itinerary.getDays().stream()
                .map(day -> new Day(
                        day.getDayIndex(),
                        day.getItems().stream()
                                .map(item -> new Item(
                                        item.getContentId(),
                                        item.getTitle(),
                                        item.getOrderIndex(),
                                        item.getReason(),
                                        item.getVisitStart(),
                                        item.getVisitEnd()
                                ))
                                .toList(),
                        day.getTravelMinutes(),
                        day.getTravelKm()
                ))
                .toList();

        return new SharedItineraryResponse(
                itinerary.getTitle(),
                itinerary.getRegion(),
                itinerary.getTravelDate(),
                itinerary.getDuration(),
                days
        );
    }
}
