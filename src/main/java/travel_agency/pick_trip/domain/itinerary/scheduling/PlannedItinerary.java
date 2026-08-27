package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.util.List;

/**
 * 스케줄링이 끝난 전체 여행 일정이다.
 * {@code adjustments}는 AI 원안을 영업시간·이동시간 제약으로 보정한 내역이며, 사용자 안내 문구로 쓰인다.
 */
public record PlannedItinerary(
        String title,
        List<ScheduledDay> days,
        List<String> adjustments
) {
    public PlannedItinerary {
        days = days == null ? List.of() : List.copyOf(days);
        adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
    }
}
