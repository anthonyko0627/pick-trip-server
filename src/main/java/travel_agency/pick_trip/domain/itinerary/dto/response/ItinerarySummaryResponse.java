package travel_agency.pick_trip.domain.itinerary.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 저장된 일정 목록 조회용 요약 응답. {@code days} 는 포함하지 않는다.
 * 일차·항목까지 포함한 상세는 {@link ItineraryResponse} 가 담당한다.
 */
public record ItinerarySummaryResponse(
        UUID itineraryId,
        String title,
        Region region,
        LocalDate travelDate,
        Integer duration,
        LocalDateTime lastModifiedAt
) {

    public static ItinerarySummaryResponse from(Itinerary itinerary) {
        return new ItinerarySummaryResponse(
                itinerary.getItineraryId(),
                itinerary.getTitle(),
                itinerary.getRegion(),
                itinerary.getTravelDate(),
                itinerary.getDuration(),
                itinerary.getLastModifiedAt()
        );
    }
}
