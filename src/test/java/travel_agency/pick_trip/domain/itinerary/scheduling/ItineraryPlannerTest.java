package travel_agency.pick_trip.domain.itinerary.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryPlannerTest {

    /** 2026-03-02 은 월요일이다. 1일차 월요일 / 2일차 화요일이 되도록 기준일로 삼는다. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    @Test
    @DisplayName("바구니에 없는 contentId 는 AI 환각으로 보고 제거한다.")
    void dropContentIdsOutsideBasket() {
        // given
        SchedulingPlace real = place("1", "실재하는 곳");
        Map<String, SchedulingPlace> basket = Map.of("1", real);

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("1", "999")), basket, Map.of(), MONDAY, SchedulingContext.car(null));

        // then
        assertThat(itinerary.days()).hasSize(1);
        assertThat(itinerary.days().get(0).stops())
                .extracting(ScheduledStop::contentId)
                .containsExactly("1");
    }

    @Test
    @DisplayName("여러 일차에 중복된 장소는 첫 등장만 남긴다.")
    void keepOnlyFirstOccurrenceOfDuplicatedPlace() {
        // given
        Map<String, SchedulingPlace> basket = Map.of(
                "a", place("a", "A"), "b", place("b", "B"), "c", place("c", "C"));

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("a", "b"), List.of("b", "c")), basket, Map.of(), null,
                SchedulingContext.car(null));

        // then
        assertThat(itinerary.days().get(0).stops())
                .extracting(ScheduledStop::contentId).containsExactly("a", "b");
        assertThat(itinerary.days().get(1).stops())
                .extracting(ScheduledStop::contentId).containsExactly("c");
    }

    @Test
    @DisplayName("방문일이 휴무인 장소는 가장 가까운 영업 일차로 옮기고 조정 내역을 남긴다.")
    void moveStopClosedOnItsVisitDay() {
        // given
        SchedulingPlace closedOnMonday = new SchedulingPlace(
                "m", "월요일휴무관", null, null, null,
                new OperatingHours(null, null, Set.of(DayOfWeek.MONDAY), true), 90, false);
        Map<String, SchedulingPlace> basket = Map.of("m", closedOnMonday, "x", place("x", "상시관"));

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("m"), List.of("x")), basket, Map.of(), MONDAY, SchedulingContext.car(null));

        // then
        assertThat(itinerary.days().get(0).stops()).isEmpty();
        assertThat(itinerary.days().get(1).stops())
                .extracting(ScheduledStop::contentId).containsExactly("x", "m");
        assertThat(itinerary.adjustments())
                .containsExactly("'월요일휴무관'은 1일차(월요일)에 휴무여서 2일차로 옮겼습니다.");
    }

    @Test
    @DisplayName("모든 일차가 휴무면 장소를 그대로 두고 해당 스톱에 경고를 남긴다.")
    void keepStopWhenClosedOnEveryDay() {
        // given
        SchedulingPlace alwaysClosed = new SchedulingPlace(
                "m", "주중휴무관", null, null, null,
                new OperatingHours(null, null, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), true), 90, false);
        Map<String, SchedulingPlace> basket = Map.of("m", alwaysClosed, "x", place("x", "상시관"));

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("m"), List.of("x")), basket, Map.of(), MONDAY, SchedulingContext.car(null));

        // then
        assertThat(itinerary.days().get(0).stops()).hasSize(1);
        assertThat(itinerary.days().get(0).stops().get(0).notes()).contains("방문 예정일이 휴무일입니다.");
        assertThat(itinerary.adjustments()).isEmpty();
    }

    @Test
    @DisplayName("여행 날짜가 없으면 휴무일 검증을 건너뛰고 날짜 없는 일정을 만든다.")
    void skipClosedDayCheckWithoutTravelDate() {
        // given
        SchedulingPlace closedOnMonday = new SchedulingPlace(
                "m", "월요일휴무관", null, null, null,
                new OperatingHours(null, null, Set.of(DayOfWeek.MONDAY), true), 90, false);
        Map<String, SchedulingPlace> basket = Map.of("m", closedOnMonday);

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("m")), basket, Map.of(), null, SchedulingContext.car(null));

        // then
        assertThat(itinerary.days().get(0).date()).isNull();
        assertThat(itinerary.days().get(0).stops()).hasSize(1);
        assertThat(itinerary.days().get(0).stops().get(0).notes()).isEmpty();
        assertThat(itinerary.adjustments()).containsExactly("여행 날짜가 없어 휴무일 검증을 건너뛰었습니다.");
    }

    @Test
    @DisplayName("입력이 모두 null 이어도 빈 일정을 돌려준다.")
    void normalizeNullInputs() {
        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan("여행", null, null, null, null, SchedulingContext.car(null));

        // then
        assertThat(itinerary.title()).isEqualTo("여행");
        assertThat(itinerary.days()).isEmpty();
        assertThat(itinerary.adjustments()).containsExactly("여행 날짜가 없어 휴무일 검증을 건너뛰었습니다.");
    }

    @Test
    @DisplayName("일차별 날짜는 여행 시작일 기준으로 매겨진다.")
    void assignDatesFromTravelDate() {
        // given
        Map<String, SchedulingPlace> basket = Map.of("a", place("a", "A"), "b", place("b", "B"));

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("a"), List.of("b")), basket, Map.of(), MONDAY, SchedulingContext.car(null));

        // then
        assertThat(itinerary.days()).extracting(ScheduledDay::dayIndex).containsExactly(1, 2);
        assertThat(itinerary.days()).extracting(ScheduledDay::date)
                .containsExactly(MONDAY, MONDAY.plusDays(1));
    }

    @Test
    @DisplayName("시작 지점을 지정하면 좌표 기준으로 일차 배분을 다시 나눈다.")
    void redistributeDaysFromStartPlace() {
        // given
        // AI 는 a-d / b-c 로 나눴지만 위도상으로는 a-b / c-d 가 자연스럽다.
        Map<String, SchedulingPlace> basket = coordinatePlaces();

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("a", "d"), List.of("b", "c")), basket, Map.of(), null,
                SchedulingContext.car("a"));

        // then
        assertThat(itinerary.days().get(0).stops())
                .extracting(ScheduledStop::contentId).containsExactly("a", "b");
        assertThat(itinerary.days().get(1).stops())
                .extracting(ScheduledStop::contentId).containsExactly("c", "d");
    }

    @Test
    @DisplayName("시작 지점이 다르면 일차 배분과 순서가 함께 달라진다.")
    void routeChangesWithStartPlace() {
        // given
        Map<String, SchedulingPlace> basket = coordinatePlaces();

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("a", "d"), List.of("b", "c")), basket, Map.of(), null,
                SchedulingContext.car("d"));

        // then
        assertThat(itinerary.days().get(0).stops())
                .extracting(ScheduledStop::contentId).containsExactly("d", "c");
        assertThat(itinerary.days().get(1).stops())
                .extracting(ScheduledStop::contentId).containsExactly("b", "a");
    }

    @Test
    @DisplayName("시작 지점을 지정하지 않아도 좌표 기준으로 일차 배분을 다시 나눈다.")
    void redistributeDaysWithoutStartPlace() {
        // given
        // AI 는 멀리 떨어진 a-d 를 같은 일차에 넣었지만, 서버는 인접한 것끼리 묶어야 한다.
        Map<String, SchedulingPlace> basket = coordinatePlaces();

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("a", "d"), List.of("b", "c")), basket, Map.of(), null,
                SchedulingContext.car(null));

        // then
        assertThat(itinerary.days().get(0).stops())
                .extracting(ScheduledStop::contentId).containsExactly("a", "b");
        assertThat(itinerary.days().get(1).stops())
                .extracting(ScheduledStop::contentId).containsExactly("c", "d");
    }

    @Test
    @DisplayName("시작 지점을 지정하지 않으면 첫 스톱도 순서 최적화 대상이 된다.")
    void optimizeFirstStopWithoutStartPlace() {
        // given
        // 좌표 보유 첫 장소가 b 라 nearest-neighbor 는 b 에서 출발하지만, 앵커가 없으니 첫 자리도 바꿀 수 있다.
        Map<String, SchedulingPlace> basket = Map.of(
                "a", coordinatePlace("a", 35.0),
                "b", coordinatePlace("b", 35.1),
                "c", coordinatePlace("c", 35.2));

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("b", "a", "c")), basket, Map.of(), null, SchedulingContext.car(null));

        // then
        // b 에서 출발하면 왕복이라 75분, 첫 자리까지 재배치하면 50분이다.
        assertThat(itinerary.days().get(0).stops())
                .extracting(ScheduledStop::contentId).containsExactly("a", "b", "c");
        assertThat(itinerary.days().get(0).totalTravelMinutes()).isEqualTo(50);
    }

    @Test
    @DisplayName("재배분해도 일차 수는 입력과 동일하게 유지된다.")
    void keepDayCountAfterRedistribution() {
        // given
        Map<String, SchedulingPlace> basket = coordinatePlaces();
        List<List<String>> aiPlan = List.of(List.of("a", "b", "c", "d"), List.of(), List.of());

        // when
        PlannedItinerary withStart = ItineraryPlanner.plan(
                "여행", aiPlan, basket, Map.of(), null, SchedulingContext.car("d"));
        PlannedItinerary withoutStart = ItineraryPlanner.plan(
                "여행", aiPlan, basket, Map.of(), null, SchedulingContext.car(null));

        // then
        assertThat(withStart.days()).hasSize(3);
        assertThat(withoutStart.days()).hasSize(3);
    }

    @Test
    @DisplayName("일차 재배분 뒤에도 휴무일 재배치가 그대로 동작한다.")
    void moveClosedStopsAfterRedistribution() {
        // given
        // 재배분하면 1일차(월요일)에 a-m 이 묶이지만, m 은 월요일 휴무라 2일차로 밀려야 한다.
        SchedulingPlace closedOnMonday = new SchedulingPlace(
                "m", "월요일휴무관", null, 35.05, 127.0,
                new OperatingHours(null, null, Set.of(DayOfWeek.MONDAY), true), 90, false);
        Map<String, SchedulingPlace> basket = Map.of(
                "a", coordinatePlace("a", 35.0),
                "b", coordinatePlace("b", 35.5),
                "m", closedOnMonday);

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("a", "b"), List.of("m")), basket, Map.of(), MONDAY,
                SchedulingContext.car("a"));

        // then
        assertThat(itinerary.days().get(0).stops())
                .extracting(ScheduledStop::contentId).containsExactly("a");
        assertThat(itinerary.days().get(1).stops())
                .extracting(ScheduledStop::contentId).containsExactlyInAnyOrder("b", "m");
        assertThat(itinerary.adjustments())
                .containsExactly("'월요일휴무관'은 1일차(월요일)에 휴무여서 2일차로 옮겼습니다.");
    }

    @Test
    @DisplayName("'꼭 가기' 장소는 재배분 뒤에도 빠짐없이 일정에 남는다.")
    void keepAllMustVisitPlaces() {
        // given
        Map<String, SchedulingPlace> basket = Map.of(
                "a", coordinatePlace("a", 35.0),
                "b", mustVisitPlace("b", 35.4),
                "c", coordinatePlace("c", 35.1),
                "d", mustVisitPlace("d", 35.3));

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("a", "b"), List.of("c", "d")), basket, Map.of(), null,
                SchedulingContext.car("a"));

        // then
        assertThat(itinerary.days().stream()
                .flatMap(day -> day.stops().stream())
                .map(ScheduledStop::contentId))
                .containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    private static Map<String, SchedulingPlace> coordinatePlaces() {
        return Map.of(
                "a", coordinatePlace("a", 35.0),
                "b", coordinatePlace("b", 35.1),
                "c", coordinatePlace("c", 35.2),
                "d", coordinatePlace("d", 35.3));
    }

    private static SchedulingPlace coordinatePlace(String contentId, double latitude) {
        return new SchedulingPlace(
                contentId, contentId, null, latitude, 127.0, OperatingHours.unknown(), 90, false);
    }

    private static SchedulingPlace mustVisitPlace(String contentId, double latitude) {
        return new SchedulingPlace(
                contentId, contentId, null, latitude, 127.0, OperatingHours.unknown(), 90, true);
    }

    private static SchedulingPlace place(String contentId, String title) {
        return new SchedulingPlace(contentId, title, null, null, null, OperatingHours.unknown(), 90, false);
    }
}
