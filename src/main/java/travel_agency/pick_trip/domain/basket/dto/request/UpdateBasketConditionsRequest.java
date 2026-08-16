package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Set;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 여행 조건 저장/갱신 요청. 부분 갱신이며, {@code null} 인 필드는 "변경 없음"으로 기존 값을 유지한다.
 *
 * <p>값을 보낼 경우에는 검증한다. {@code duration} 이 {@code 0} 이하이면
 * {@code 400 VALIDATION_FAILED} 로 거부한다.
 */
public record UpdateBasketConditionsRequest(
        Region region,
        LocalDate travelDate,
        @Positive Integer duration,
        Set<TravelCondition> companions
) {
}
