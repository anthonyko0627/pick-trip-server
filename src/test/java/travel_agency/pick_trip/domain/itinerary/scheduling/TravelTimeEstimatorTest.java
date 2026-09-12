package travel_agency.pick_trip.domain.itinerary.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TravelTimeEstimatorTest {

    private static final double DEFAULT_CAR_SPEED = SchedulingPolicy.AVG_SPEED_KMH;

    @Test
    @DisplayName("거리가 0km 이면 이동 시간은 0분이다.")
    void returnZeroMinutesForZeroDistance() {
        // given
        double km = 0.0;

        // when
        int minutes = TravelTimeEstimator.minutes(km, TravelMode.CAR, DEFAULT_CAR_SPEED);

        // then
        assertThat(minutes).isZero();
    }

    @Test
    @DisplayName("자동차 35km 이동은 우회 계수를 반영해 78분으로 계산한다.")
    void applyDetourFactorToTravelTime() {
        // given
        // 35km * 1.3 = 45.5km, 45.5km / 35km/h = 1.3h, 1.3h * 60 = 78분
        double km = 35.0;

        // when
        int minutes = TravelTimeEstimator.minutes(km, TravelMode.CAR, DEFAULT_CAR_SPEED);

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
        int minutes = TravelTimeEstimator.minutes(km, TravelMode.CAR, DEFAULT_CAR_SPEED);

        // then
        assertThat(minutes).isEqualTo(1);
    }

    @Test
    @DisplayName("음수 거리는 0분으로 처리한다.")
    void returnZeroMinutesForNegativeDistance() {
        // given
        double km = -5.0;

        // when
        int minutes = TravelTimeEstimator.minutes(km, TravelMode.CAR, DEFAULT_CAR_SPEED);

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
        int nanMinutes = TravelTimeEstimator.minutes(nan, TravelMode.CAR, DEFAULT_CAR_SPEED);
        int infiniteMinutes = TravelTimeEstimator.minutes(infinite, TravelMode.CAR, DEFAULT_CAR_SPEED);

        // then
        assertThat(nanMinutes).isZero();
        assertThat(infiniteMinutes).isZero();
    }

    @Test
    @DisplayName("같은 거리라도 대중교통이 자동차보다 오래 걸린다.")
    void transitTakesLongerThanCarForSameDistance() {
        // given
        double km = 1.0;

        // when
        int carMinutes = TravelTimeEstimator.minutes(km, TravelMode.CAR, DEFAULT_CAR_SPEED);
        int transitMinutes = TravelTimeEstimator.minutes(km, TravelMode.TRANSIT, DEFAULT_CAR_SPEED);

        // then
        assertThat(carMinutes).isLessThan(transitMinutes);
    }

    @Test
    @DisplayName("대중교통에서 도보 한계 이하 구간은 도보 속도로 환산한다.")
    void useWalkSpeedWithinWalkLimit() {
        // given
        // 1.5km * 1.3 = 1.95km 로 도보 한계(2.0km) 안이다. 1.95km / 3.5km/h * 60 = 33.4분, 올림해 34분.
        double km = 1.5;

        // when
        int minutes = TravelTimeEstimator.minutes(km, TravelMode.TRANSIT, DEFAULT_CAR_SPEED);

        // then
        assertThat(minutes).isEqualTo(34);
    }

    @Test
    @DisplayName("대중교통에서 도보 한계를 넘는 구간은 대기 시간을 더한 버스 속도로 환산한다.")
    void useBusApproximationBeyondWalkLimit() {
        // given
        // 10km * 1.3 = 13km 로 도보 한계를 넘는다. 15분 대기 + 13km / 25km/h * 60 = 15 + 31.2 → 15 + 32 = 47분.
        double km = 10.0;

        // when
        int minutes = TravelTimeEstimator.minutes(km, TravelMode.TRANSIT, DEFAULT_CAR_SPEED);

        // then
        assertThat(minutes).isEqualTo(47);
    }

    @Test
    @DisplayName("자동차는 관측 평균 속도를 주면 그 속도로 환산한다.")
    void useObservedSpeedForCar() {
        // given
        // 10km * 1.3 = 13km, 13km / 50km/h * 60 = 15.6분, 올림해 16분.
        double km = 10.0;
        double observedSpeedKmh = 50.0;

        // when
        int minutes = TravelTimeEstimator.minutes(km, TravelMode.CAR, observedSpeedKmh);

        // then
        assertThat(minutes).isEqualTo(16);
    }

    @Test
    @DisplayName("관측 속도가 비정상이면 기본 평균 속도로 되돌린다.")
    void fallbackToDefaultSpeedWhenObservedSpeedIsInvalid() {
        // given
        double km = 35.0;

        // when
        int zeroSpeedMinutes = TravelTimeEstimator.minutes(km, TravelMode.CAR, 0.0);
        int nanSpeedMinutes = TravelTimeEstimator.minutes(km, TravelMode.CAR, Double.NaN);

        // then
        assertThat(zeroSpeedMinutes).isEqualTo(78);
        assertThat(nanSpeedMinutes).isEqualTo(78);
    }
}
