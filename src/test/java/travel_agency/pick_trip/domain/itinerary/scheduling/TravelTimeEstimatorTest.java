package travel_agency.pick_trip.domain.itinerary.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TravelTimeEstimatorTest {

    @Test
    @DisplayName("거리가 0km 이면 이동 시간은 0분이다.")
    void returnZeroMinutesForZeroDistance() {
        // given
        double km = 0.0;

        // when
        int minutes = TravelTimeEstimator.minutes(km);

        // then
        assertThat(minutes).isZero();
    }

    @Test
    @DisplayName("35km 이동은 우회 계수를 반영해 78분으로 계산한다.")
    void applyDetourFactorToTravelTime() {
        // given
        // 35km * 1.3 = 45.5km, 45.5km / 35km/h = 1.3h, 1.3h * 60 = 78분
        double km = 35.0;

        // when
        int minutes = TravelTimeEstimator.minutes(km);

        // then
        assertThat(minutes).isEqualTo(78);
    }

    @Test
    @DisplayName("거리가 아주 짧아도 이동 시간은 최소 1분이다.")
    void returnAtLeastOneMinuteForVeryShortDistance() {
        // given
        // 0.1km * 1.3 / 35 * 60 = 0.223분, 올림해 1분이 된다.
        double km = 0.1;

        // when
        int minutes = TravelTimeEstimator.minutes(km);

        // then
        assertThat(minutes).isEqualTo(1);
    }

    @Test
    @DisplayName("음수 거리는 0분으로 처리한다.")
    void returnZeroMinutesForNegativeDistance() {
        // given
        double km = -5.0;

        // when
        int minutes = TravelTimeEstimator.minutes(km);

        // then
        assertThat(minutes).isZero();
    }

    @Test
    @DisplayName("NaN 이나 무한대 거리는 0분으로 처리한다.")
    void returnZeroMinutesForNonFiniteDistance() {
        // given
        double nan = Double.NaN;
        double infinite = Double.POSITIVE_INFINITY;

        // when
        int nanMinutes = TravelTimeEstimator.minutes(nan);
        int infiniteMinutes = TravelTimeEstimator.minutes(infinite);

        // then
        assertThat(nanMinutes).isZero();
        assertThat(infiniteMinutes).isZero();
    }
}
