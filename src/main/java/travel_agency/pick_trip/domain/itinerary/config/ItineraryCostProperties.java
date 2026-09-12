package travel_agency.pick_trip.domain.itinerary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 일정안 교통비 추정에 쓰는 단가. 유가·요금은 바뀌므로 코드 상수가 아닌 설정값으로 둔다.
 * 값이 비어 있어도 부팅은 성공해야 하므로 여기서 기본값으로 되돌린다.
 *
 * @param carCostPerKmWon    자동차 1km 당 비용(원). 유가와 연비를 합친 값이다.
 * @param transitBaseFareWon 지역 시내버스 기본요금(원)
 */
@ConfigurationProperties(prefix = "itinerary.cost")
public record ItineraryCostProperties(Integer carCostPerKmWon, Integer transitBaseFareWon) {

    /** 휘발유 1,700원/L ÷ 연비 12km/L ≒ 142원/km. */
    private static final int DEFAULT_CAR_COST_PER_KM_WON = 142;

    /** 경남·경북 시내버스 성인 현금 기본요금(2025년 기준 1,500원). */
    private static final int DEFAULT_TRANSIT_BASE_FARE_WON = 1500;

    public ItineraryCostProperties {
        // 0 이하가 들어오면 교통비가 0 원이나 음수로 나와 비교 지표로 쓸 수 없다.
        carCostPerKmWon = (carCostPerKmWon == null || carCostPerKmWon <= 0)
                ? DEFAULT_CAR_COST_PER_KM_WON : carCostPerKmWon;
        transitBaseFareWon = (transitBaseFareWon == null || transitBaseFareWon <= 0)
                ? DEFAULT_TRANSIT_BASE_FARE_WON : transitBaseFareWon;
    }
}
