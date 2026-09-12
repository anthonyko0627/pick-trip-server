package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 확정된 일정안 하나에서 비교 지표({@link VariantMetrics})를 뽑아낸다.
 * 스케줄링 결과에 이미 들어있는 값(일차별 이동 시간·거리)은 다시 계산하지 않고 합만 낸다.
 *
 * <p>교통비는 실측 요금이 아니라 상수 기반 개략 추정이다.
 * <ul>
 *   <li>자동차: {@code 총 이동 km × km당 비용}. 하동·영주·예천은 국도 비중이 높아 통행료는 0 으로 근사한다.</li>
 *   <li>대중교통: {@code 시내버스 기본요금 × 승차 횟수}. 승차 횟수는 도보로 감당할 수 없는
 *       ({@link SchedulingPolicy#WALK_MAX_KM} 초과) 구간 수다.
 *       국내에 지역 시내버스 요금 공개 API 가 없어 환승 할인·거리 비례(시계외) 요금을 반영하지 못한다.</li>
 * </ul>
 */
public final class VariantMetricsCalculator {

    /** 사유 코드 맵의 키. 응답 필드명과 같아야 클라이언트가 지표와 사유를 이어붙일 수 있다. */
    private static final String METRIC_TOTAL_TRANSIT_COST = "totalTransitCost";

    private VariantMetricsCalculator() {
    }

    /**
     * @param planned            스케줄링이 끝난 일정안
     * @param context            이 안을 만들 때 쓴 컨텍스트. 구간 거리는 {@link SchedulingContext#legBetween}
     *                           으로만 구해 스케줄링과 같은 기준을 쓴다.
     * @param placesById         구간 거리 계산에 필요한 좌표 원본. 스톱은 contentId 만 들고 있다.
     * @param carCostPerKmWon    자동차 km 당 비용(원). 유가·연비를 합친 설정값이다.
     * @param transitBaseFareWon 지역 시내버스 기본요금(원). 설정값이다.
     */
    public static VariantMetrics calculate(PlannedItinerary planned,
                                           SchedulingContext context,
                                           Map<String, SchedulingPlace> placesById,
                                           int carCostPerKmWon,
                                           int transitBaseFareWon) {
        Map<String, SchedulingPlace> places = (placesById == null) ? Map.of() : placesById;
        boolean transit = context.travelMode() == TravelMode.TRANSIT;

        int totalTravelMinutes = 0;
        double totalTravelKm = 0.0;
        int placeCount = 0;
        int walkingMinutes = 0;
        int boardings = 0;
        int legCount = 0;
        int measuredLegCount = 0;

        for (ScheduledDay day : planned.days()) {
            totalTravelMinutes += day.totalTravelMinutes();
            totalTravelKm += day.totalTravelKm();
            List<ScheduledStop> stops = day.stops();
            placeCount += stops.size();

            for (int i = 0; i + 1 < stops.size(); i++) {
                legCount++;
                SchedulingPlace from = places.get(stops.get(i).contentId());
                SchedulingPlace to = places.get(stops.get(i + 1).contentId());
                TravelMatrix.Leg leg = (from == null || to == null) ? null : context.legBetween(from, to);
                if (leg == null) {
                    // 좌표가 없어 거리를 모르는 구간. 도보·승차 어느 쪽으로도 셀 수 없어 제외한다.
                    continue;
                }
                measuredLegCount++;
                if (!transit) {
                    continue;
                }
                // 도보/승차 판정은 소요 시간을 나눌 때와 같은 술어를 써야 한다. 경계가 갈라지면
                // "도보 40분"인데 요금이 붙는 식으로 지표끼리 어긋난다.
                if (TravelTimeEstimator.isWalkLeg(leg.km(), context.travelMode())) {
                    walkingMinutes += leg.minutes();
                } else {
                    boardings++;
                }
            }
        }

        // 구간이 있는데 하나도 재지 못했으면 거리 기반 비용을 낼 근거가 없다.
        // 구간이 아예 없으면(스톱 0~1개) 이동 자체가 없는 것이므로 0 원이 맞다.
        boolean costUnavailable = legCount > 0 && measuredLegCount == 0;
        Map<String, String> unavailableReasons = new LinkedHashMap<>();
        if (costUnavailable) {
            unavailableReasons.put(
                    METRIC_TOTAL_TRANSIT_COST, MetricUnavailableReason.UNKNOWN_TRAVEL_DISTANCE.name());
        }

        Integer transitCost = costUnavailable
                ? null
                : (transit
                        // ponytail: 요금 공개 API 가 없어 환승 할인·거리 비례 요금을 반영하지 못하는 개략 추정이다.
                        // ODsay 등 대중교통 길찾기 API 를 붙이면 실측 요금으로 교체한다.
                        ? boardings * transitBaseFareWon
                        // ponytail: 좌표 폴백 구간의 거리는 직선거리라 실제 주행거리보다 짧게 잡힌다(과소 추정).
                        // 도로 거리 행렬 적중률이 올라가면 자연히 해소된다.
                        : (int) Math.round(totalTravelKm * carCostPerKmWon));

        return new VariantMetrics(
                totalTravelMinutes,
                // 자동차 안은 주차 후 도보가 모델에 없어 언제나 0 이다. 산출 불가가 아니므로 사유 코드도 없다.
                transit ? walkingMinutes : 0,
                transitCost,
                placeCount,
                unavailableReasons);
    }
}
