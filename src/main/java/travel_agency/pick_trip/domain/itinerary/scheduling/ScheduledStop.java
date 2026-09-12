package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.time.LocalTime;
import java.util.List;

/**
 * 하루 일정 안에서 방문 순서·시간이 확정된 장소 한 곳을 표현한다.
 * {@code reason}은 AI 가 제시한 배치 이유(자동 삽입 휴식이면 삽입 이유), {@code notes}는 스케줄러가 붙인 보정 안내다.
 *
 * @param autoRest 체력 안배로 서버가 자동 삽입한 휴식 스톱이면 true. 사용자가 저장 전 지울 수 있도록 응답에서 구분한다.
 */
public record ScheduledStop(
        String contentId,
        String title,
        int order,
        String reason,
        LocalTime startTime,
        LocalTime endTime,
        List<String> notes,
        boolean autoRest
) {
    public ScheduledStop {
        // 응답 조립 쪽에서 null 검사 없이 순회할 수 있도록 불변 빈 리스트로 정규화한다.
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** 일반 스톱(자동 삽입 아님). 호출부 대부분이 이 형태라 축약 생성자를 둔다. */
    public ScheduledStop(String contentId, String title, int order, String reason,
                         LocalTime startTime, LocalTime endTime, List<String> notes) {
        this(contentId, title, order, reason, startTime, endTime, notes, false);
    }
}
