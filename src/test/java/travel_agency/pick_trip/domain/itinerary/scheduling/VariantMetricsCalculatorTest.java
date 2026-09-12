package travel_agency.pick_trip.domain.itinerary.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VariantMetricsCalculator")
class VariantMetricsCalculatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    /** 휘발유 1,700원/L ÷ 연비 12km/L 근사. 설정 기본값과 같은 값으로 검증한다. */
    private static final int CAR_COST_PER_KM_WON = 142;
    private static final int TRANSIT_BASE_FARE_WON = 1500;

    @Test
    @DisplayName("자동차 안은 총 이동시간·장소수와 함께 주행거리 기반 교통비를 낸다.")
    void carMetrics() {
        // given - 위도 0.1도(약 11.12km) 간격 세 곳. 하루 이동 50분 / 22.24km
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.1, 127.0),
                place("3", "C", 35.2, 127.0));
        SchedulingContext context = SchedulingContext.car(null);

        // when
        VariantMetrics metrics = calculate(context, List.of(places));

        // then
        assertThat(metrics.totalTravelMinutes()).isEqualTo(50);
        assertThat(metrics.placeCount()).isEqualTo(3);
        // 22.24km * 142원/km = 3,158원
        assertThat(metrics.totalTransitCost()).isEqualTo(3158);
        assertThat(metrics.unavailableReasons()).isEmpty();
    }

    @Test
    @DisplayName("대중교통 안은 도보 구간 시간을 합치고 승차 구간만 요금으로 계산한다.")
    void transitMetrics() {
        // given - A→B 는 약 1.11km(도보 25분), B→C 는 약 11.12km(버스 50분)
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.01, 127.0),
                place("3", "C", 35.11, 127.0));
        SchedulingContext context = new SchedulingContext(TravelMode.TRANSIT, null, null);

        // when
        VariantMetrics metrics = calculate(context, List.of(places));

        // then
        assertThat(metrics.totalTravelMinutes()).isEqualTo(75);
        assertThat(metrics.totalWalkingMinutes()).isEqualTo(25);
        // 승차는 B→C 한 번뿐이다.
        assertThat(metrics.totalTransitCost()).isEqualTo(TRANSIT_BASE_FARE_WON);
        assertThat(metrics.placeCount()).isEqualTo(3);
        assertThat(metrics.unavailableReasons()).isEmpty();
    }

    @Test
    @DisplayName("대중교통 안에서 도보 한계 거리 앞뒤 구간이 도보와 승차로 갈린다.")
    void splitsWalkAndRideAtWalkLimit() {
        // given - 우회 계수 1.3 적용 후 각각 약 1.95km(한계 이하) / 약 2.10km(한계 초과)
        List<SchedulingPlace> walkable = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.0135, 127.0));
        List<SchedulingPlace> rideable = List.of(
                place("3", "C", 36.0, 127.0),
                place("4", "D", 36.0145, 127.0));
        SchedulingContext context = new SchedulingContext(TravelMode.TRANSIT, null, null);

        // when
        VariantMetrics metrics = calculate(context, List.of(walkable, rideable));

        // then
        assertThat(metrics.totalWalkingMinutes()).isEqualTo(34);
        assertThat(metrics.totalTravelMinutes()).isEqualTo(55);
        // 요금이 붙는 구간은 한계를 넘은 C→D 하나다.
        assertThat(metrics.totalTransitCost()).isEqualTo(TRANSIT_BASE_FARE_WON);
    }

    @Test
    @DisplayName("자동차 안의 도보 시간은 걸어갈 만한 거리여도 0이며 사유 코드를 남기지 않는다.")
    void carHasNoWalkingMinutes() {
        // given - 약 1.11km 로 대중교통이라면 도보로 분류될 거리
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.01, 127.0));

        // when
        VariantMetrics metrics = calculate(SchedulingContext.car(null), List.of(places));

        // then
        assertThat(metrics.totalWalkingMinutes()).isZero();
        assertThat(metrics.totalTransitCost()).isNotNull();
        assertThat(metrics.unavailableReasons()).isEmpty();
    }

    @Test
    @DisplayName("좌표가 없어 이동 거리를 하나도 재지 못하면 교통비는 null 과 사유 코드로 응답한다.")
    void reportsUnavailableCostWhenNoDistanceIsKnown() {
        // given
        List<SchedulingPlace> places = List.of(
                place("1", "A", null, null),
                place("2", "B", null, null),
                place("3", "C", null, null));

        // when
        VariantMetrics metrics = calculate(SchedulingContext.car(null), List.of(places));

        // then
        assertThat(metrics.totalTransitCost()).isNull();
        assertThat(metrics.unavailableReasons())
                .containsExactly(entry("totalTransitCost",
                        MetricUnavailableReason.UNKNOWN_TRAVEL_DISTANCE.name()));
        // 나머지 지표는 그대로 산출된다(좌표 미상 구간도 고정 20분으로 잡힌다).
        assertThat(metrics.totalTravelMinutes()).isEqualTo(40);
        assertThat(metrics.placeCount()).isEqualTo(3);
        assertThat(metrics.totalWalkingMinutes()).isZero();
    }

    @Test
    @DisplayName("일부 구간만 좌표가 없으면 잰 구간만으로 교통비를 근사한다.")
    void estimatesCostWhenOnlySomeLegsAreUnknown() {
        // given - A→B 는 잴 수 있고(약 1.11km), 좌표 없는 C 로 가는 구간만 거리를 모른다
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.01, 127.0),
                place("3", "C", null, null));

        // when
        VariantMetrics metrics = calculate(SchedulingContext.car(null), List.of(places));

        // then
        assertThat(metrics.totalTransitCost()).isNotNull();
        assertThat(metrics.unavailableReasons()).isEmpty();
    }

    @Test
    @DisplayName("스톱이 없거나 하나뿐이면 이동이 없는 것이므로 교통비는 0원이다.")
    void treatsNoLegAsZeroCost() {
        // given
        List<SchedulingPlace> single = List.of(place("1", "A", 35.0, 127.0));

        // when
        VariantMetrics metrics = calculate(SchedulingContext.car(null), List.of(List.of(), single));

        // then
        assertThat(metrics.totalTravelMinutes()).isZero();
        assertThat(metrics.totalWalkingMinutes()).isZero();
        assertThat(metrics.totalTransitCost()).isZero();
        assertThat(metrics.placeCount()).isEqualTo(1);
        assertThat(metrics.unavailableReasons()).isEmpty();
    }

    @Test
    @DisplayName("이동수단이 달라도 지표 필드는 모두 채워져 스플릿 뷰가 같은 키로 비교할 수 있다.")
    void keepsSameMetricKeysAcrossTravelModes() {
        // given
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.1, 127.0));

        // when
        VariantMetrics car = calculate(SchedulingContext.car(null), List.of(places));
        VariantMetrics transit =
                calculate(new SchedulingContext(TravelMode.TRANSIT, null, null), List.of(places));

        // then - record 이므로 필드 구성이 같고, 값도 모드와 무관하게 전부 채워진다.
        assertThat(car).hasNoNullFieldsOrProperties();
        assertThat(transit).hasNoNullFieldsOrProperties();
        assertThat(car.totalTravelMinutes()).isLessThan(transit.totalTravelMinutes());
    }

    /** 하루씩 실제 스케줄러를 돌려 일정안을 만든 뒤 지표를 계산한다. */
    private static VariantMetrics calculate(SchedulingContext context, List<List<SchedulingPlace>> days) {
        List<ScheduledDay> scheduled = new ArrayList<>();
        Map<String, SchedulingPlace> placesById = new LinkedHashMap<>();
        for (int i = 0; i < days.size(); i++) {
            scheduled.add(DayScheduler.schedule(i + 1, DATE, days.get(i), Map.of(), context));
            days.get(i).forEach(place -> placesById.put(place.contentId(), place));
        }
        return VariantMetricsCalculator.calculate(
                new PlannedItinerary("테스트 일정", scheduled, List.of()),
                context,
                placesById,
                CAR_COST_PER_KM_WON,
                TRANSIT_BASE_FARE_WON);
    }

    private static SchedulingPlace place(String contentId, String title, Double latitude, Double longitude) {
        return new SchedulingPlace(contentId, title, null, latitude, longitude, OperatingHours.unknown(), 90, false);
    }
}
