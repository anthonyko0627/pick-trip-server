package travel_agency.pick_trip.domain.itinerary.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchedulingContext")
class SchedulingContextTest {

    private static SchedulingPlace place(String contentId, double latitude) {
        return new SchedulingPlace(
                contentId, contentId, null, latitude, 127.0, OperatingHours.unknown(), 90, false);
    }

    private static SchedulingPlace noCoordinates(String contentId) {
        return new SchedulingPlace(
                contentId, contentId, null, null, null, OperatingHours.unknown(), 90, false);
    }

    private static TravelMatrix matrixOf(String from, String to, double km, int minutes) {
        return new TravelMatrix(Map.of(TravelMatrix.key(from, to), new TravelMatrix.Leg(km, minutes)), 50.0);
    }

    @Test
    @DisplayName("도로 행렬에 값이 있으면 직선거리 대신 실측값을 쓴다.")
    void useRoadMatrixWhenAvailable() {
        // given
        SchedulingPlace a = place("a", 35.0);
        SchedulingPlace b = place("b", 35.1);
        SchedulingContext context =
                new SchedulingContext(TravelMode.CAR, matrixOf("a", "b", 13.0, 21), null);

        // when
        TravelMatrix.Leg leg = context.legBetween(a, b);

        // then
        assertThat(leg.km()).isEqualTo(13.0);
        assertThat(leg.minutes()).isEqualTo(21);
    }

    @Test
    @DisplayName("도로 행렬에 없는 구간은 직선거리와 관측 평균 속도로 환산한다.")
    void fallbackToStraightDistanceWithObservedSpeed() {
        // given - a-b 만 실측했고 b-a 는 빠져 있다.
        SchedulingPlace a = place("a", 35.0);
        SchedulingPlace b = place("b", 35.1);
        SchedulingContext context =
                new SchedulingContext(TravelMode.CAR, matrixOf("a", "b", 13.0, 21), null);

        // when
        TravelMatrix.Leg leg = context.legBetween(b, a);

        // then
        // 직선 약 11.1km * 1.3 = 14.5km, 관측 속도 50km/h 로 18분. 기본 35km/h 였다면 25분이다.
        assertThat(leg.km()).isCloseTo(11.1, org.assertj.core.data.Offset.offset(0.1));
        assertThat(leg.minutes()).isEqualTo(18);
    }

    @Test
    @DisplayName("대중교통 안은 자동차 도로 행렬을 쓰지 않는다.")
    void ignoreRoadMatrixForTransit() {
        // given
        SchedulingPlace a = place("a", 35.0);
        SchedulingPlace b = place("b", 35.1);
        SchedulingContext context =
                new SchedulingContext(TravelMode.TRANSIT, matrixOf("a", "b", 13.0, 21), null);

        // when
        TravelMatrix.Leg leg = context.legBetween(a, b);

        // then
        assertThat(leg.km()).isNotEqualTo(13.0);
        assertThat(leg.minutes()).isGreaterThan(21);
    }

    @Test
    @DisplayName("좌표가 없는 장소가 끼면 구간을 계산하지 못한다.")
    void returnNullWhenCoordinatesMissing() {
        // given
        SchedulingContext context = SchedulingContext.car(null);

        // when
        TravelMatrix.Leg leg = context.legBetween(place("a", 35.0), noCoordinates("b"));

        // then
        assertThat(leg).isNull();
    }

    @Test
    @DisplayName("이동수단과 행렬을 주지 않으면 자동차·빈 행렬로 정규화한다.")
    void normalizeNullInputs() {
        // given
        SchedulingContext context = new SchedulingContext(null, null, null);

        // when
        TravelMode mode = context.travelMode();
        TravelMatrix matrix = context.travelMatrix();

        // then
        assertThat(mode).isEqualTo(TravelMode.CAR);
        assertThat(matrix.legs()).isEmpty();
        assertThat(matrix.fallbackSpeedKmh()).isEqualTo(SchedulingPolicy.AVG_SPEED_KMH);
        assertThat(context.elevationProfile().metersByCoordinate()).isEmpty();
    }

    // --- 경사 페널티 (#72) ---

    /** 위도 35.0 선상에서 경도만 벌린 두 지점. 0.01도는 약 0.91km 로 도보 경계(2km) 안이다. */
    private static SchedulingPlace atLongitude(String contentId, double longitude) {
        return new SchedulingPlace(
                contentId, contentId, null, 35.0, longitude, OperatingHours.unknown(), 90, false);
    }

    private static SchedulingContext transitWithElevations(Map<Double, Double> metersByLongitude) {
        Map<String, Double> profile = new java.util.LinkedHashMap<>();
        metersByLongitude.forEach((longitude, meters) ->
                profile.put(ElevationProfile.key(35.0, longitude), meters));
        return new SchedulingContext(
                TravelMode.TRANSIT, TravelMatrix.empty(), null, new ElevationProfile(profile));
    }

    @Test
    @DisplayName("도보 구간이 오르막이면 평지보다 이동 시간이 경사 페널티만큼 길어진다.")
    void addSlopePenaltyToUphillWalkLeg() {
        // given - 상승 300m 는 Naismith 근사(600m/h)로 정확히 30분이다.
        SchedulingPlace a = atLongitude("a", 127.0);
        SchedulingPlace b = atLongitude("b", 127.01);
        SchedulingContext flat = transitWithElevations(Map.of(127.0, 100.0, 127.01, 100.0));
        SchedulingContext uphill = transitWithElevations(Map.of(127.0, 100.0, 127.01, 400.0));

        // when
        TravelMatrix.Leg flatLeg = flat.legBetween(a, b);
        TravelMatrix.Leg uphillLeg = uphill.legBetween(a, b);

        // then
        assertThat(uphillLeg.minutes()).isEqualTo(flatLeg.minutes() + 30);
        assertThat(uphillLeg.km()).isEqualTo(flatLeg.km());
    }

    @Test
    @DisplayName("내리막 도보 구간에는 경사 페널티가 붙지 않는다.")
    void noPenaltyOnDownhillWalkLeg() {
        // given
        SchedulingPlace a = atLongitude("a", 127.0);
        SchedulingPlace b = atLongitude("b", 127.01);
        SchedulingContext downhill = transitWithElevations(Map.of(127.0, 400.0, 127.01, 100.0));

        // when
        TravelMatrix.Leg leg = downhill.legBetween(a, b);

        // then
        assertThat(leg.minutes()).isEqualTo(
                new SchedulingContext(TravelMode.TRANSIT, null, null).legBetween(a, b).minutes());
    }

    @Test
    @DisplayName("도보 경계를 넘는 대중교통 구간에는 경사 페널티가 붙지 않는다.")
    void noPenaltyBeyondWalkBoundary() {
        // given - 경도 0.05도는 약 4.55km 로 우회 계수를 곱하면 도보 경계(2km)를 넘는다.
        SchedulingPlace a = atLongitude("a", 127.0);
        SchedulingPlace far = atLongitude("far", 127.05);
        SchedulingContext uphill = transitWithElevations(Map.of(127.0, 100.0, 127.05, 400.0));

        // when
        TravelMatrix.Leg leg = uphill.legBetween(a, far);

        // then
        assertThat(TravelTimeEstimator.isWalkLeg(leg.km(), TravelMode.TRANSIT)).isFalse();
        assertThat(leg.minutes()).isEqualTo(
                new SchedulingContext(TravelMode.TRANSIT, null, null).legBetween(a, far).minutes());
    }

    @Test
    @DisplayName("자동차 구간에는 경사 페널티가 붙지 않는다.")
    void noPenaltyForCarLeg() {
        // given
        SchedulingPlace a = atLongitude("a", 127.0);
        SchedulingPlace b = atLongitude("b", 127.01);
        ElevationProfile steep = new ElevationProfile(Map.of(
                ElevationProfile.key(35.0, 127.0), 100.0,
                ElevationProfile.key(35.0, 127.01), 400.0));
        SchedulingContext car = new SchedulingContext(TravelMode.CAR, TravelMatrix.empty(), null, steep);

        // when
        TravelMatrix.Leg leg = car.legBetween(a, b);

        // then
        assertThat(leg.minutes()).isEqualTo(SchedulingContext.car(null).legBetween(a, b).minutes());
    }

    @Test
    @DisplayName("고도를 전부 모르면 경사 페널티 없이 기존과 같은 이동 시간이 나온다.")
    void unknownElevationsKeepPreviousMinutes() {
        // given
        SchedulingPlace a = atLongitude("a", 127.0);
        SchedulingPlace b = atLongitude("b", 127.01);
        SchedulingContext unknown = new SchedulingContext(
                TravelMode.TRANSIT, TravelMatrix.empty(), null, ElevationProfile.unknown());

        // when
        TravelMatrix.Leg leg = unknown.legBetween(a, b);

        // then
        assertThat(leg.minutes()).isEqualTo(
                new SchedulingContext(TravelMode.TRANSIT, null, null).legBetween(a, b).minutes());
    }
}
