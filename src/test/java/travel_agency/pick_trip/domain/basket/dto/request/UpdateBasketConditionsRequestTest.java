package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.region.Region;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부분 갱신이므로 {@code null} 은 "변경 없음"으로 허용하되, 값을 보냈다면 검증한다.
 * 누락된 duration 이 저장된 값을 지우던 문제(#43)는 {@code Basket.updateConditions} 쪽에서
 * 막으며, 그 동작은 {@code BasketTest} 가 검증한다.
 */
@DisplayName("UpdateBasketConditionsRequest")
class UpdateBasketConditionsRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private Set<ConstraintViolation<UpdateBasketConditionsRequest>> validateDuration(Integer duration) {
        UpdateBasketConditionsRequest request = new UpdateBasketConditionsRequest(
                Region.YEONGJU,
                LocalDate.of(2026, 8, 20),
                duration,
                Set.of(TravelCondition.WITH_PARENTS)
        );
        return validator.validate(request);
    }

    @Test
    @DisplayName("duration 이 null 이면 변경 없음이므로 검증을 통과한다.")
    void acceptNullDuration() {
        // given
        Integer duration = null;

        // when
        Set<ConstraintViolation<UpdateBasketConditionsRequest>> violations = validateDuration(duration);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("duration 이 0 이면 검증에 실패한다.")
    void rejectZeroDuration() {
        // given
        Integer duration = 0;

        // when
        Set<ConstraintViolation<UpdateBasketConditionsRequest>> violations = validateDuration(duration);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("duration");
    }

    @Test
    @DisplayName("duration 이 음수이면 검증에 실패한다.")
    void rejectNegativeDuration() {
        // given
        Integer duration = -1;

        // when
        Set<ConstraintViolation<UpdateBasketConditionsRequest>> violations = validateDuration(duration);

        // then
        assertThat(violations)
                .isNotEmpty()
                .extracting(v -> v.getPropertyPath().toString())
                .contains("duration");
    }

    @Test
    @DisplayName("duration 이 양수이면 검증을 통과한다.")
    void acceptPositiveDuration() {
        // given
        Integer duration = 3;

        // when
        Set<ConstraintViolation<UpdateBasketConditionsRequest>> violations = validateDuration(duration);

        // then
        assertThat(violations).isEmpty();
    }
}
