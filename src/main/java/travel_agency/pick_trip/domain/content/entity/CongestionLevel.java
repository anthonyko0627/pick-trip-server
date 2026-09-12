package travel_agency.pick_trip.domain.content.entity;

/**
 * 콘텐츠 혼잡 레벨. 산출 규칙과 임계값은
 * {@link travel_agency.pick_trip.domain.content.service.CongestionCalculator} 에 있다.
 */
public enum CongestionLevel {
    LOW,
    MEDIUM,
    HIGH
}
