package travel_agency.pick_trip.domain.itinerary.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DaySchedulerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    @Test
    @DisplayName("지그재그 순서를 총 이동시간이 가장 짧은 순서로 재정렬한다.")
    void reorderStopsToMinimizeTravelTime() {
        // given
        // 위도 0.1도 = 약 11.12km, 이동 25분. A-C-B(75분) 대신 A-B-C(50분)가 최소다.
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("3", "C", 35.2, 127.0),
                place("2", "B", 35.1, 127.0));

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car(null));

        // then
        assertThat(day.stops()).extracting(ScheduledStop::title).containsExactly("A", "B", "C");
        assertThat(day.stops()).extracting(ScheduledStop::order).containsExactly(1, 2, 3);
        assertThat(day.totalTravelMinutes()).isEqualTo(50);
        assertThat(day.totalTravelKm()).isEqualTo(22.24);
    }

    @Test
    @DisplayName("폐장 전 관람이 불가능한 순서 대신 하드 위반이 없는 순서를 고른다.")
    void preferOrderWithoutOperatingHourViolation() {
        // given
        // 이른 마감 장소를 뒤에 두면 10:50 도착 + 90분 = 12:20 으로 11:00 폐장을 넘긴다.
        SchedulingPlace earlyClose = new SchedulingPlace(
                "1", "이른마감관", null, null, null,
                new OperatingHours(9 * 60, 11 * 60, Set.of(), true), 90, false);
        SchedulingPlace anytime = new SchedulingPlace(
                "2", "종일관", null, null, null, OperatingHours.unknown(), 90, false);

        // when
        ScheduledDay day = DayScheduler.schedule(
                1, DATE, List.of(anytime, earlyClose), Map.of(), SchedulingContext.car(null));

        // then
        assertThat(day.stops()).extracting(ScheduledStop::title).containsExactly("이른마감관", "종일관");
        assertThat(day.stops().get(0).notes()).isEmpty();
    }

    @Test
    @DisplayName("개장 전에 도착하면 대기 안내가 붙고 시작 시각이 개장 시각으로 밀린다.")
    void delayStartUntilOpeningTime() {
        // given
        SchedulingPlace lateOpen = new SchedulingPlace(
                "1", "늦개장관", null, null, null,
                new OperatingHours(10 * 60 + 30, 18 * 60, Set.of(), true), 90, false);

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, List.of(lateOpen), Map.of(), SchedulingContext.car(null));

        // then
        ScheduledStop stop = day.stops().get(0);
        assertThat(stop.startTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(stop.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(stop.notes()).containsExactly("개장 전 도착이라 10:30까지 대기가 필요합니다.");
    }

    @Test
    @DisplayName("폐장 시각을 넘겨도 장소를 빼지 않고 안내만 남긴다.")
    void keepStopWhenClosingTimeExceeded() {
        // given
        SchedulingPlace earlyClose = new SchedulingPlace(
                "1", "이른마감관", null, null, null,
                new OperatingHours(9 * 60, 10 * 60, Set.of(), true), 90, false);

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, List.of(earlyClose), Map.of(), SchedulingContext.car(null));

        // then
        ScheduledStop stop = day.stops().get(0);
        assertThat(stop.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(stop.endTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(stop.notes()).containsExactly("운영시간(~10:00)을 넘겨 관람이 어려울 수 있습니다.");
    }

    @Test
    @DisplayName("좌표가 없는 구간은 20분으로 잡고 총 이동거리에는 넣지 않는다.")
    void useFixedMinutesForUnknownCoordinateHop() {
        // given
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", null, null),
                place("3", "C", 35.1, 127.0));

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car(null));

        // then
        assertThat(day.stops()).extracting(ScheduledStop::title).containsExactly("A", "B", "C");
        assertThat(day.totalTravelMinutes()).isEqualTo(40);
        assertThat(day.totalTravelKm()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("장소가 8개 이상이면 nearest-neighbor + 2-opt 로 이동시간을 줄인다.")
    void optimizeOrderWithNearestNeighborWhenTooManyStops() {
        // given
        // AI 순서대로면 첫 두 구간에서만 1.3도(약 145km)를 왕복하지만, 위도 순으로 꿰면 0.7도로 끝난다.
        List<SchedulingPlace> places = lineOfEightPlaces();

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car(null));

        // then
        assertThat(day.stops()).extracting(ScheduledStop::title)
                .containsExactly("P0", "P2", "P3", "P4", "P5", "P6", "P7", "P1");
        // AI 순서 그대로면 448분이므로 최적화 후 이동시간이 초기 순서보다 크지 않다.
        assertThat(day.totalTravelMinutes()).isEqualTo(175);
        assertThat(day.totalTravelKm()).isCloseTo(77.84, within(0.01));
    }

    @Test
    @DisplayName("시작 지점을 지정하면 그 장소를 첫 스톱으로 고정한 채 순서를 최적화한다.")
    void pinStartPlaceAsFirstStop() {
        // given
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.1, 127.0),
                place("3", "C", 35.2, 127.0));

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car("3"));

        // then
        assertThat(day.stops()).extracting(ScheduledStop::title).containsExactly("C", "B", "A");
        assertThat(day.totalTravelMinutes()).isEqualTo(50);
    }

    @Test
    @DisplayName("장소가 8개 이상이어도 시작 지점은 첫 스톱으로 고정된다.")
    void pinStartPlaceWithManyStops() {
        // given
        List<SchedulingPlace> places = lineOfEightPlaces();

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car("4"));

        // then
        assertThat(day.stops().get(0).title()).isEqualTo("P4");
        assertThat(day.stops()).extracting(ScheduledStop::title)
                .containsExactlyInAnyOrder("P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7");
    }

    @Test
    @DisplayName("시작 지점이 이 일차에 없으면 앵커 고정 없이 기존처럼 순서를 고른다.")
    void ignoreStartPlaceMissingFromDay() {
        // given
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("3", "C", 35.2, 127.0),
                place("2", "B", 35.1, 127.0));

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car("없는id"));

        // then
        assertThat(day.stops()).extracting(ScheduledStop::title).containsExactly("A", "B", "C");
    }

    /** 위도 순으로 늘어놓으면 최단인데 AI 가 두 번째에 가장 먼 곳을 끼워 넣은 8개 배치. */
    private static List<SchedulingPlace> lineOfEightPlaces() {
        double[] latitudes = {35.0, 35.7, 35.1, 35.2, 35.3, 35.4, 35.5, 35.6};
        List<SchedulingPlace> places = new ArrayList<>();
        for (int i = 0; i < latitudes.length; i++) {
            places.add(place(String.valueOf(i), "P" + i, latitudes[i], 127.0));
        }
        return places;
    }

    @Test
    @DisplayName("장소가 없으면 예외 없이 빈 하루 일정을 돌려준다.")
    void returnEmptyDayWhenNoPlaces() {
        // when
        ScheduledDay day = DayScheduler.schedule(2, DATE, List.of(), Map.of(), SchedulingContext.car(null));

        // then
        assertThat(day.dayIndex()).isEqualTo(2);
        assertThat(day.date()).isEqualTo(DATE);
        assertThat(day.stops()).isEmpty();
        assertThat(day.totalTravelMinutes()).isZero();
        assertThat(day.totalTravelKm()).isZero();
        assertThat(day.dayNotes()).isEmpty();
    }

    @Test
    @DisplayName("일정이 아무리 길어도 시각이 23:59 를 넘어 자정을 돌지 않는다.")
    void clampTimesAtEndOfDay() {
        // given
        // 300분 체류 7곳 + 20분 이동 6구간 = 2220분으로 09:00 기준 자정을 크게 넘는다.
        List<SchedulingPlace> places = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            places.add(new SchedulingPlace(
                    String.valueOf(i), "P" + i, null, null, null, OperatingHours.unknown(), 300, false));
        }

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car(null));

        // then
        assertThat(day.stops()).allSatisfy(stop -> {
            assertThat(stop.startTime()).isBeforeOrEqualTo(LocalTime.of(23, 59));
            assertThat(stop.endTime()).isBeforeOrEqualTo(LocalTime.of(23, 59));
            assertThat(stop.endTime()).isAfterOrEqualTo(stop.startTime());
        });
        assertThat(day.dayNotes()).contains("하루 일정이 너무 길어 시각이 잘렸습니다. 장소를 줄이거나 일차를 늘려보세요.");
    }

    @Test
    @DisplayName("장거리 단일 이동과 과도한 하루 총 이동시간을 경고한다.")
    void warnAboutLongTravel() {
        // given
        // 위도 0.9도 = 약 100km, 우회 보정 후 224분(3시간 44분)
        List<SchedulingPlace> places = List.of(
                place("1", "출발지", 35.0, 127.0),
                place("2", "도착지", 35.9, 127.0));

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car(null));

        // then
        assertThat(day.totalTravelMinutes()).isEqualTo(224);
        assertThat(day.totalTravelKm()).isCloseTo(100.08, within(0.01));
        assertThat(day.dayNotes()).containsExactly(
                "'출발지'에서 '도착지'까지 약 100km로 이동 부담이 큽니다.",
                "하루 총 이동시간이 약 3시간 44분입니다.");
    }

    @Test
    @DisplayName("AI 가 준 배치 이유를 스톱에 그대로 담는다.")
    void carryReasonFromAi() {
        // given
        List<SchedulingPlace> places = List.of(place("1", "A", 35.0, 127.0));

        // when
        ScheduledDay day = DayScheduler.schedule(1, DATE, places, Map.of("1", "지역 대표 명소"), SchedulingContext.car(null));

        // then
        assertThat(day.stops().get(0).reason()).isEqualTo("지역 대표 명소");
    }

    @Test
    @DisplayName("같은 장소라도 대중교통 일정안의 하루 이동시간이 자동차보다 길다.")
    void transitTakesLongerThanCarForSameDay() {
        // given
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.1, 127.0));

        // when
        ScheduledDay car = DayScheduler.schedule(1, DATE, places, Map.of(), SchedulingContext.car(null));
        ScheduledDay transit = DayScheduler.schedule(
                1, DATE, places, Map.of(), new SchedulingContext(TravelMode.TRANSIT, null, null));

        // then
        assertThat(car.totalTravelMinutes()).isLessThan(transit.totalTravelMinutes());
    }

    @Test
    @DisplayName("도로 행렬이 있으면 직선거리 대신 실측 도로 거리·시간으로 하루 이동량을 계산한다.")
    void useRoadMatrixForDayTotals() {
        // given - 직선 약 11.12km 구간을 도로 20km·40분으로 실측한 행렬
        List<SchedulingPlace> places = List.of(
                place("1", "A", 35.0, 127.0),
                place("2", "B", 35.1, 127.0));
        TravelMatrix matrix = new TravelMatrix(
                Map.of(TravelMatrix.key("1", "2"), new TravelMatrix.Leg(20.0, 40)), 35.0);

        // when
        ScheduledDay day = DayScheduler.schedule(
                1, DATE, places, Map.of(), new SchedulingContext(TravelMode.CAR, matrix, "1"));

        // then
        assertThat(day.totalTravelMinutes()).isEqualTo(40);
        assertThat(day.totalTravelKm()).isEqualTo(20.0);
    }

    private static SchedulingPlace place(String contentId, String title, Double latitude, Double longitude) {
        return new SchedulingPlace(contentId, title, null, latitude, longitude, OperatingHours.unknown(), 90, false);
    }
}
