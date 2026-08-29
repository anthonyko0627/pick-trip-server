package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.time.LocalDate;
import java.util.List;

/**
 * 하루치 확정 일정과 그날의 총 이동량을 표현한다.
 * {@code date}는 사용자가 여행 시작일을 지정하지 않은 경우 null 이다.
 */
public record ScheduledDay(
        int dayIndex,
        LocalDate date,
        List<ScheduledStop> stops,
        int totalTravelMinutes,
        double totalTravelKm,
        List<String> dayNotes
) {
    public ScheduledDay {
        stops = stops == null ? List.of() : List.copyOf(stops);
        dayNotes = dayNotes == null ? List.of() : List.copyOf(dayNotes);
    }
}
