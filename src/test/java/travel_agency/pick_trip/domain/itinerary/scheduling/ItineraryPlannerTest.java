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
                "여행", List.of(List.of("1", "999")), basket, Map.of(), MONDAY);

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
                "여행", List.of(List.of("a", "b"), List.of("b", "c")), basket, Map.of(), null);

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
                new OperatingHours(null, null, Set.of(DayOfWeek.MONDAY), true), 90);
        Map<String, SchedulingPlace> basket = Map.of("m", closedOnMonday, "x", place("x", "상시관"));

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("m"), List.of("x")), basket, Map.of(), MONDAY);

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
                new OperatingHours(null, null, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), true), 90);
        Map<String, SchedulingPlace> basket = Map.of("m", alwaysClosed, "x", place("x", "상시관"));

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("m"), List.of("x")), basket, Map.of(), MONDAY);

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
                new OperatingHours(null, null, Set.of(DayOfWeek.MONDAY), true), 90);
        Map<String, SchedulingPlace> basket = Map.of("m", closedOnMonday);

        // when
        PlannedItinerary itinerary = ItineraryPlanner.plan(
                "여행", List.of(List.of("m")), basket, Map.of(), null);

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
        PlannedItinerary itinerary = ItineraryPlanner.plan("여행", null, null, null, null);

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
                "여행", List.of(List.of("a"), List.of("b")), basket, Map.of(), MONDAY);

        // then
        assertThat(itinerary.days()).extracting(ScheduledDay::dayIndex).containsExactly(1, 2);
        assertThat(itinerary.days()).extracting(ScheduledDay::date)
                .containsExactly(MONDAY, MONDAY.plusDays(1));
    }

    private static SchedulingPlace place(String contentId, String title) {
        return new SchedulingPlace(contentId, title, null, null, null, OperatingHours.unknown(), 90);
    }
}
