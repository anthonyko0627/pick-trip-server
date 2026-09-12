package travel_agency.pick_trip.domain.itinerary.scheduling;

/**
 * 도보 구간의 오르막 부담(누적 상승고도·평균 경사)과 그로 인한 추가 소요 시간, 휴식 필요 여부를 계산한다.
 *
 * <p>스케줄링 패키지를 순수 코드로 유지하기 위해 고도 값을 인자로 받기만 한다.
 * 고도 조회는 {@code domain.itinerary.service.ElevationResolver} 가 담당하며,
 * 고도를 모르는 구간은 상승고도 0·페널티 0 으로 취급해 일정이 깨지지 않게 한다.
 */
public final class WalkEffort {

    /**
     * 도보 leg 의 누적 상승고도(m). 내리막·평지는 0이다.
     *
     * <p>ponytail: 출발·도착 두 점의 고도 차이만 본다. 언덕을 넘었다가 내려오는 지형은
     * 중간 상승분이 통째로 빠져 실제보다 과소평가된다. 경로 폴리라인 위 샘플 고도를 받아올 수 있게 되면
     * (길찾기 응답의 경유 좌표를 함께 조회) 그때 구간 합산으로 올린다.
     */
    public static double climbMeters(Double fromElevationMeters, Double toElevationMeters) {
        // 한쪽이라도 고도 미상이면 차이를 알 수 없으므로 오르막이 없는 것으로 본다.
        if (fromElevationMeters == null || toElevationMeters == null) {
            return 0.0;
        }

        // 해수면 아래(음수 고도)여도 차이만 쓰므로 그대로 성립한다. NaN 이 흘러들면 0으로 막는다.
        double climb = toElevationMeters - fromElevationMeters;
        return (!Double.isFinite(climb) || climb <= 0) ? 0.0 : climb;
    }

    /**
     * 평균 경사(상승고도 ÷ 수평거리). 0.05 면 5% 경사다.
     * 수평거리가 0이면 기울기를 정의할 수 없으므로(수직 상승은 도보 leg 로 성립하지 않는다) 0으로 본다.
     */
    public static double averageGrade(double climbMeters, double horizontalKm) {
        if (!Double.isFinite(climbMeters) || climbMeters <= 0
                || !Double.isFinite(horizontalKm) || horizontalKm <= 0) {
            return 0.0;
        }
        return climbMeters / (horizontalKm * 1000.0);
    }

    /**
     * Naismith 규칙 근사로 계산한 오르막 추가 시간(분). 평지·내리막·고도 미상은 0분이다.
     * 상수 근거는 {@link SchedulingPolicy#NAISMITH_CLIMB_METERS_PER_HOUR} 참고.
     */
    public static int slopePenaltyMinutes(double climbMeters) {
        if (!Double.isFinite(climbMeters) || climbMeters <= 0) {
            return 0;
        }
        return (int) Math.round(climbMeters / SchedulingPolicy.NAISMITH_CLIMB_METERS_PER_HOUR * 60);
    }

    /**
     * 마지막 휴식 이후 누적된 도보 시간·상승고도가 임계를 넘었는지 판정한다.
     * 사유를 구분해 돌려주므로 호출부가 삽입 안내 문구를 골라 쓸 수 있다.
     *
     * @param cumulativeWalkMinutes   마지막 휴식 이후 누적 도보 시간(분)
     * @param cumulativeClimbMeters   마지막 휴식 이후 누적 상승고도(m). 고도 미상 구간은 0으로 누적된다.
     */
    public static RestReason restReason(int cumulativeWalkMinutes, double cumulativeClimbMeters) {
        boolean byWalkTime = cumulativeWalkMinutes >= SchedulingPolicy.REST_WALK_MINUTES;
        boolean byClimb = Double.isFinite(cumulativeClimbMeters)
                && cumulativeClimbMeters >= SchedulingPolicy.REST_CLIMB_METERS;

        if (byWalkTime && byClimb) {
            return RestReason.BOTH;
        }
        if (byWalkTime) {
            return RestReason.WALK_TIME;
        }
        if (byClimb) {
            return RestReason.CLIMB;
        }
        return RestReason.NONE;
    }

    /** 휴식 스톱 삽입 사유. {@code NONE} 이면 아직 임계에 못 미쳐 삽입하지 않는다. */
    public enum RestReason {
        NONE,
        /** 누적 도보 시간 초과. */
        WALK_TIME,
        /** 누적 상승고도 초과. */
        CLIMB,
        /** 둘 다 초과. */
        BOTH;

        public boolean needsRest() {
            return this != NONE;
        }
    }

    private WalkEffort() {
    }
}
