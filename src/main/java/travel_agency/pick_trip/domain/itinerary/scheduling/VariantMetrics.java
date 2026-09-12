package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.util.Map;

/**
 * 일정안 하나의 비교 지표. 스플릿 뷰에서 안끼리 나란히 놓고 비교하는 값이다.
 *
 * <p>record 라서 모든 안이 같은 키 집합을 반환한다. <b>산출할 수 없는 지표도 필드를 빼지 않고
 * 값을 {@code null} 로 두며, 그 이유를 {@code unavailableReasons} 에 사유 코드로 남긴다.</b>
 * 스플릿 뷰가 열별로 값을 맞춰 그리려면 안마다 필드 구성이 같아야 하기 때문이다.
 * 예: {@code {"totalTransitCost": "UNKNOWN_TRAVEL_DISTANCE"}}. 전부 산출되면 빈 맵이다.
 *
 * <p>"해당 없음"은 산출 불가가 아니다. 예를 들어 자동차 안의 도보 시간은 모델에 주차 후 도보가 없어
 * 언제나 0 이며, 사유 코드를 넣지 않는다.
 *
 * @param totalTravelMinutes 전 일차 이동 시간(분) 합
 * @param totalWalkingMinutes 도보로 분류된 구간의 시간(분) 합
 * @param totalTransitCost 총 예상 교통비(원). 상수 기반 개략 추정이다.
 * @param placeCount 전 일차 방문 장소 수
 * @param unavailableReasons 지표명 → {@link MetricUnavailableReason} name
 */
public record VariantMetrics(
        Integer totalTravelMinutes,
        Integer totalWalkingMinutes,
        Integer totalTransitCost,
        Integer placeCount,
        Map<String, String> unavailableReasons
) {
    public VariantMetrics {
        unavailableReasons = unavailableReasons == null ? Map.of() : Map.copyOf(unavailableReasons);
    }
}
