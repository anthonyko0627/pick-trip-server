package travel_agency.pick_trip.domain.itinerary.scheduling;

/**
 * 지표를 산출하지 못한 이유. 응답에는 enum name 을 문자열로 내려보낸다.
 * 사유 문구를 응답 조립부마다 문자열 리터럴로 흩뿌리지 않기 위해 여기 모은다.
 */
public enum MetricUnavailableReason {

    /**
     * 구간 좌표(또는 도로 거리)가 하나도 없어 이동 거리를 잴 근거가 없다.
     * 거리를 모르면 자동차 유류비도, 대중교통 승차 횟수도 계산할 수 없다.
     */
    UNKNOWN_TRAVEL_DISTANCE
}
