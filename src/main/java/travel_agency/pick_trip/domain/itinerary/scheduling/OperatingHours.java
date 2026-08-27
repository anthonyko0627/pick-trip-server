package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * 장소의 운영 시간·휴무 정보. TourAPI 원문 파싱 결과를 담는 값 객체다.
 * 원문이 지저분해 부분 파싱만 성공하는 경우가 잦아 각 필드는 독립적으로 null 일 수 있다.
 *
 * @param openMinuteOfDay  개장 시각(0~1439). null 이면 미상
 * @param closeMinuteOfDay 폐장 시각(0~1439). null 이면 미상
 * @param closedDays       매주 확정 휴무 요일. 비어 있으면 휴무 정보 없음 또는 연중무휴
 * @param parsed           원문에서 하나라도 의미를 읽어냈으면 true
 */
public record OperatingHours(
        Integer openMinuteOfDay,
        Integer closeMinuteOfDay,
        Set<DayOfWeek> closedDays,
        boolean parsed
) {

    private static final OperatingHours UNKNOWN = new OperatingHours(null, null, Set.of(), false);

    public OperatingHours {
        // 호출부가 null 이나 가변 Set 을 넘겨도 이후 스케줄링 로직이 방어 코드를 갖지 않도록 여기서 정규화한다.
        closedDays = (closedDays == null) ? Set.of() : Set.copyOf(closedDays);
    }

    public static OperatingHours unknown() {
        return UNKNOWN;
    }

    public boolean hasHours() {
        return openMinuteOfDay != null && closeMinuteOfDay != null;
    }

    public boolean isClosedOn(DayOfWeek day) {
        return day != null && closedDays.contains(day);
    }
}
