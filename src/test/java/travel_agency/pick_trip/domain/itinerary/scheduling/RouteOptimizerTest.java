package travel_agency.pick_trip.domain.itinerary.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOptimizerTest {

    @Test
    @DisplayName("시작 지점에서 가까운 순서로 꿴 뒤 일수만큼 잘라 배분한다.")
    void redistributeFromStartPlace() {
        // given
        // 위도 순으로 a-b-c-d 가 일렬로 놓여 있고, AI 는 지리와 무관하게 나눠 놓았다.
        Map<String, SchedulingPlace> places = places(
                place("a", 35.0), place("b", 35.1), place("c", 35.2), place("d", 35.3));
        List<List<String>> aiPlan = List.of(List.of("a", "d"), List.of("b", "c"));

        // when
        List<List<String>> result = RouteOptimizer.redistribute(aiPlan, places, SchedulingContext.car("a"));

        // then
        assertThat(result).containsExactly(List.of("a", "b"), List.of("c", "d"));
    }

    @Test
    @DisplayName("시작 지점이 반대편이면 일차 배분도 반대로 뒤집힌다.")
    void redistributeChangesWithStartPlace() {
        // given
        Map<String, SchedulingPlace> places = places(
                place("a", 35.0), place("b", 35.1), place("c", 35.2), place("d", 35.3));
        List<List<String>> aiPlan = List.of(List.of("a", "d"), List.of("b", "c"));

        // when
        List<List<String>> result = RouteOptimizer.redistribute(aiPlan, places, SchedulingContext.car("d"));

        // then
        assertThat(result).containsExactly(List.of("d", "c"), List.of("b", "a"));
    }

    @Test
    @DisplayName("장소 수가 일수로 나누어떨어지지 않으면 앞 일차부터 하나씩 더 배분한다.")
    void distributeRemainderToEarlierDays() {
        // given
        Map<String, SchedulingPlace> places = places(
                place("a", 35.0), place("b", 35.1), place("c", 35.2),
                place("d", 35.3), place("e", 35.4));
        List<List<String>> aiPlan = List.of(List.of("a", "b", "c", "d", "e"), List.of());

        // when
        List<List<String>> result = RouteOptimizer.redistribute(aiPlan, places, SchedulingContext.car("a"));

        // then
        assertThat(result).containsExactly(List.of("a", "b", "c"), List.of("d", "e"));
    }

    @Test
    @DisplayName("일수가 장소 수보다 많으면 뒤쪽 일차는 비워 두고 일차 수는 유지한다.")
    void keepDayCountWhenPlacesAreFewer() {
        // given
        Map<String, SchedulingPlace> places = places(place("a", 35.0), place("b", 35.1));
        List<List<String>> aiPlan = List.of(List.of("a", "b"), List.of(), List.of());

        // when
        List<List<String>> result = RouteOptimizer.redistribute(aiPlan, places, SchedulingContext.car("a"));

        // then
        assertThat(result).containsExactly(List.of("a"), List.of("b"), List.of());
    }

    @Test
    @DisplayName("좌표가 없는 장소는 거리를 잴 수 없어 원래 순서대로 경로 뒤에 붙는다.")
    void appendPlacesWithoutCoordinatesAtTheEnd() {
        // given
        Map<String, SchedulingPlace> places = places(
                place("a", 35.0), noCoordinates("n1"), place("b", 35.2), noCoordinates("n2"));
        List<List<String>> aiPlan = List.of(List.of("a", "n1", "b", "n2"));

        // when
        List<List<String>> result = RouteOptimizer.redistribute(aiPlan, places, SchedulingContext.car("a"));

        // then
        assertThat(result).containsExactly(List.of("a", "b", "n1", "n2"));
    }

    @Test
    @DisplayName("좌표가 하나도 없으면 원래 순서를 그대로 배분한다.")
    void keepOriginalOrderWhenNoCoordinates() {
        // given
        Map<String, SchedulingPlace> places = places(
                noCoordinates("a"), noCoordinates("b"), noCoordinates("c"));
        List<List<String>> aiPlan = List.of(List.of("a", "b", "c"), List.of());

        // when
        List<List<String>> result = RouteOptimizer.redistribute(aiPlan, places, SchedulingContext.car("c"));

        // then
        assertThat(result).containsExactly(List.of("a", "b"), List.of("c"));
    }

    @Test
    @DisplayName("장소가 하나뿐이어도 예외 없이 배분한다.")
    void redistributeSinglePlace() {
        // given
        Map<String, SchedulingPlace> places = places(place("a", 35.0));

        // when
        List<List<String>> result = RouteOptimizer.redistribute(List.of(List.of("a")), places, SchedulingContext.car("a"));

        // then
        assertThat(result).containsExactly(List.of("a"));
    }

    @Test
    @DisplayName("시작 지점이 배분 대상에 없으면 첫 장소에서 출발한다.")
    void fallbackToFirstPlaceWhenStartIsMissing() {
        // given
        Map<String, SchedulingPlace> places = places(
                place("a", 35.0), place("b", 35.1), place("c", 35.2));
        List<List<String>> aiPlan = List.of(List.of("a", "c", "b"));

        // when
        List<List<String>> result = RouteOptimizer.redistribute(aiPlan, places, SchedulingContext.car("없는id"));

        // then
        assertThat(result).containsExactly(List.of("a", "b", "c"));
    }

    @Test
    @DisplayName("거리가 사실상 같으면 '꼭 가기' 장소를 먼저 잡는다.")
    void preferMustVisitOnTie() {
        // given
        // 시작 지점에서 양쪽으로 정확히 같은 거리에 놓인 두 후보
        Map<String, SchedulingPlace> places = places(
                place("start", 35.0), place("north", 35.1), mustVisit("south", 34.9));
        List<List<String>> aiPlan = List.of(List.of("start", "north", "south"));

        // when
        List<List<String>> result = RouteOptimizer.redistribute(aiPlan, places, SchedulingContext.car("start"));

        // then
        assertThat(result).containsExactly(List.of("start", "south", "north"));
    }

    private static Map<String, SchedulingPlace> places(SchedulingPlace... values) {
        Map<String, SchedulingPlace> map = new LinkedHashMap<>();
        for (SchedulingPlace value : values) {
            map.put(value.contentId(), value);
        }
        return map;
    }

    private static SchedulingPlace place(String contentId, double latitude) {
        return new SchedulingPlace(
                contentId, contentId, null, latitude, 127.0, OperatingHours.unknown(), 90, false);
    }

    private static SchedulingPlace mustVisit(String contentId, double latitude) {
        return new SchedulingPlace(
                contentId, contentId, null, latitude, 127.0, OperatingHours.unknown(), 90, true);
    }

    private static SchedulingPlace noCoordinates(String contentId) {
        return new SchedulingPlace(
                contentId, contentId, null, null, null, OperatingHours.unknown(), 90, false);
    }
}
