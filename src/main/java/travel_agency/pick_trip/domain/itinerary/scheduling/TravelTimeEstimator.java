package travel_agency.pick_trip.domain.itinerary.scheduling;

/**
 * 직선거리를 이동수단별 실제 이동 소요 시간(분)으로 환산한다.
 * 실측 도로 소요 시간이 있는 구간은 여기까지 오지 않고 {@link TravelMatrix} 값을 그대로 쓴다.
 */
public final class TravelTimeEstimator {

    /**
     * @param km          구간 직선거리(haversine). 우회 계수로 실제 이동 거리로 보정한 뒤 환산한다.
     * @param mode        이동수단
     * @param carSpeedKmh 자동차 환산에 쓸 평균 속도. 도로 행렬에서 관측한 값이 있으면 그 값,
     *                    없으면 {@link SchedulingPolicy#AVG_SPEED_KMH}
     */
    public static int minutes(double km, TravelMode mode, double carSpeedKmh) {
        // 좌표 미상 구간에서 NaN/무한대가 흘러들어와 일정 전체가 깨지지 않도록 방어한다.
        if (!Double.isFinite(km) || km <= 0) {
            return 0;
        }

        double actualKm = km * SchedulingPolicy.DETOUR_FACTOR;
        int estimated = (mode == TravelMode.TRANSIT)
                ? transitMinutes(km, actualKm)
                : (int) Math.ceil(actualKm / carSpeed(carSpeedKmh) * 60);

        // 거리가 있는 구간을 0분으로 표시하면 같은 시각에 두 장소에 있는 일정이 되므로 최소 1분을 보장한다.
        return Math.max(estimated, 1);
    }

    /**
     * 걸어서 이동한다고 보는 구간인지 판정한다.
     * 경사 페널티({@link SchedulingContext#legBetween})와 이동시간 환산이 같은 경계를 써야 하므로
     * 판정을 이 한 곳에만 둔다.
     *
     * @param km 구간 직선거리(haversine). 우회 계수를 적용한 실제 이동 거리로 비교한다.
     */
    public static boolean isWalkLeg(double km, TravelMode mode) {
        return mode == TravelMode.TRANSIT
                && Double.isFinite(km) && km > 0
                && km * SchedulingPolicy.DETOUR_FACTOR <= SchedulingPolicy.WALK_MAX_KM;
    }

    /**
     * 걸어서 갈 수 있는 거리는 도보 속도로, 그보다 멀면 시내버스(대기 + 표정속도)로 환산한다.
     * 전 구간을 도보로 계산하면 20km 를 여섯 시간 걷는 일정이 나와 쓸모가 없어진다.
     */
    private static int transitMinutes(double km, double actualKm) {
        if (isWalkLeg(km, TravelMode.TRANSIT)) {
            return (int) Math.ceil(actualKm / SchedulingPolicy.WALK_SPEED_KMH * 60);
        }
        // ponytail: 노선·배차를 모르는 근사. ODsay 등 대중교통 길찾기 API 를 붙이면 실측으로 교체한다.
        return SchedulingPolicy.TRANSIT_WAIT_MINUTES
                + (int) Math.ceil(actualKm / SchedulingPolicy.TRANSIT_SPEED_KMH * 60);
    }

    /** 관측 속도가 비정상으로 들어와도 0 나누기·무한대가 되지 않도록 기본 속도로 되돌린다. */
    private static double carSpeed(double carSpeedKmh) {
        return (Double.isFinite(carSpeedKmh) && carSpeedKmh > 0) ? carSpeedKmh : SchedulingPolicy.AVG_SPEED_KMH;
    }

    private TravelTimeEstimator() {
    }
}
