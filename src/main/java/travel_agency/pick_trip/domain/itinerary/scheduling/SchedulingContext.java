package travel_agency.pick_trip.domain.itinerary.scheduling;

/**
 * 일정안 하나를 스케줄링하는 동안 공유하는 입력.
 * 재배분·순서 최적화·시각 배정이 모두 같은 이동시간 모델을 봐야 해서, 파라미터를 계속 늘리는 대신 여기 묶는다.
 *
 * @param travelMode       이동수단. 구간 소요 시간 계산 방식이 갈린다.
 * @param travelMatrix     실제 도로 거리·시간 행렬. 비어 있으면 전 구간 직선거리 환산으로 폴백한다.
 * @param startContentId   여행을 시작할 장소. 앵커 고정 여부만 결정하며, 없으면 첫 스톱도 재배치 대상이 된다.
 * @param elevationProfile 좌표별 해발고도. 도보 구간의 경사 페널티에만 쓰며, 비어 있으면 페널티가 0이라
 *                         고도 조회 이전과 동일하게 동작한다.
 */
public record SchedulingContext(TravelMode travelMode, TravelMatrix travelMatrix, String startContentId,
                                ElevationProfile elevationProfile) {

    public SchedulingContext {
        // 호출부마다 null 방어를 반복하지 않도록 생성 시점에 한 번만 정규화한다.
        travelMode = travelMode == null ? TravelMode.CAR : travelMode;
        travelMatrix = travelMatrix == null ? TravelMatrix.empty() : travelMatrix;
        elevationProfile = elevationProfile == null ? ElevationProfile.unknown() : elevationProfile;
    }

    /** 고도를 조회하지 않는 호출부(도로 행렬 테스트 등)를 위한 축약 생성자. */
    public SchedulingContext(TravelMode travelMode, TravelMatrix travelMatrix, String startContentId) {
        this(travelMode, travelMatrix, startContentId, ElevationProfile.unknown());
    }

    /** 도로 행렬 없이 자동차 기준으로만 스케줄링하는 기본 컨텍스트. */
    public static SchedulingContext car(String startContentId) {
        return new SchedulingContext(TravelMode.CAR, TravelMatrix.empty(), startContentId);
    }

    /**
     * 두 장소를 잇는 구간. 도로 행렬에 값이 있으면 실측값을 그대로 쓰고, 없으면 직선거리로 환산한다.
     * 좌표가 없어 거리를 잴 근거가 아예 없으면 null 이며, 호출부는 고정 추정치로 대체한다.
     *
     * <p>도보로 분류되는 구간에는 오르막 추가 시간을 여기서 더한다. 재배분·순서 최적화·시각 배정이
     * 모두 이 메서드 하나를 거치므로, 경사 페널티도 이 한 곳에만 두면 전 경로에 일관되게 반영된다.
     */
    public TravelMatrix.Leg legBetween(SchedulingPlace from, SchedulingPlace to) {
        if (travelMode == TravelMode.CAR) {
            // 도로 행렬의 소요 시간은 자동차 경로 기준이라 대중교통 안에서는 쓰지 않는다.
            TravelMatrix.Leg road = travelMatrix.leg(from.contentId(), to.contentId());
            if (road != null) {
                return road;
            }
        }
        if (!from.hasCoordinates() || !to.hasCoordinates()) {
            return null;
        }
        double km = GeoDistance.kilometers(from.latitude(), from.longitude(), to.latitude(), to.longitude());
        int minutes = TravelTimeEstimator.minutes(km, travelMode, travelMatrix.fallbackSpeedKmh())
                + slopePenaltyMinutes(from, to, km);
        return new TravelMatrix.Leg(km, minutes);
    }

    /** 해발고도(m). 좌표가 없거나 조회하지 못한 지점이면 null(= 고도 미상). */
    public Double elevationMetersAt(SchedulingPlace place) {
        return place == null ? null : elevationProfile.metersAt(place.latitude(), place.longitude());
    }

    /**
     * 도보 구간에만 붙는 오르막 추가 시간(분). 자동차 구간은 경사 영향이 미미해 대상이 아니고,
     * 고도 미상 구간은 상승고도가 0이라 페널티도 0이 된다.
     */
    private int slopePenaltyMinutes(SchedulingPlace from, SchedulingPlace to, double km) {
        if (!TravelTimeEstimator.isWalkLeg(km, travelMode)) {
            return 0;
        }
        return WalkEffort.slopePenaltyMinutes(
                WalkEffort.climbMeters(elevationMetersAt(from), elevationMetersAt(to)));
    }
}
