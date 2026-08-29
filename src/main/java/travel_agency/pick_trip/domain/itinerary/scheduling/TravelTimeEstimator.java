package travel_agency.pick_trip.domain.itinerary.scheduling;

/**
 * 직선거리를 실제 이동 소요 시간(분)으로 환산한다.
 */
public final class TravelTimeEstimator {

    public static int minutes(double km) {
        // 좌표 미상 구간에서 NaN/무한대가 흘러들어와 일정 전체가 깨지지 않도록 방어한다.
        if (!Double.isFinite(km) || km <= 0) {
            return 0;
        }

        int estimated = (int) Math.ceil(km * SchedulingPolicy.DETOUR_FACTOR / SchedulingPolicy.AVG_SPEED_KMH * 60);

        // 거리가 있는 구간을 0분으로 표시하면 같은 시각에 두 장소에 있는 일정이 되므로 최소 1분을 보장한다.
        return Math.max(estimated, 1);
    }

    private TravelTimeEstimator() {
    }
}
