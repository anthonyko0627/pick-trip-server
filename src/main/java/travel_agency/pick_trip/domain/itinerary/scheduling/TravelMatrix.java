package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.util.Map;

/**
 * 장소 쌍별 실제 도로 거리·소요 시간을 담은 조회용 값 객체.
 *
 * <p>외부 길찾기 호출은 이 패키지 밖({@code RoadMatrixResolver})에서 끝내고, 스케줄링은 결과 값만 본다.
 * 스케줄링 패키지를 JPA·Spring 의존 없는 순수 코드로 유지하기 위함이다.
 *
 * <p>모든 쌍이 채워져 있다고 가정하지 않는다. 길찾기 실패·반경 초과로 빠진 쌍은 {@link #leg} 가 null 을
 * 돌려주며, 그 구간은 직선거리 환산으로 폴백한다. 빈 행렬({@link #empty()})이면 전 구간이 폴백이다.
 */
public record TravelMatrix(Map<String, Leg> legs, double fallbackSpeedKmh) {

    /** 한 구간의 거리(km)와 소요 시간(분). */
    public record Leg(double km, int minutes) {
    }

    public TravelMatrix {
        legs = legs == null ? Map.of() : Map.copyOf(legs);
        // 관측 속도가 없거나 비정상이면 정책 기본 속도로 되돌린다. 0 이하가 들어오면 폴백 환산이 무한대가 된다.
        if (!Double.isFinite(fallbackSpeedKmh) || fallbackSpeedKmh <= 0) {
            fallbackSpeedKmh = SchedulingPolicy.AVG_SPEED_KMH;
        }
    }

    /** 도로 거리를 하나도 확보하지 못한 경우(대중교통 안 포함). 전 구간이 직선거리 환산으로 동작한다. */
    public static TravelMatrix empty() {
        return new TravelMatrix(Map.of(), SchedulingPolicy.AVG_SPEED_KMH);
    }

    /**
     * 없으면 null. 일방통행·회전 제한 때문에 실제 도로 거리는 방향에 따라 다르므로 방향별로 따로 담는다.
     */
    public Leg leg(String fromContentId, String toContentId) {
        return legs.get(key(fromContentId, toContentId));
    }

    /** 행렬을 채우는 쪽과 읽는 쪽이 같은 규칙을 쓰도록 키 생성을 여기 둔다. */
    public static String key(String fromContentId, String toContentId) {
        return fromContentId + ">" + toContentId;
    }
}
