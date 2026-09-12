package travel_agency.pick_trip.domain.itinerary.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RestBreaks")
class RestBreaksTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    /** 위도 35.0 선상에서 경도만 벌린 지점. 간격 0.016도는 약 1.46km 로 도보 경계(2km) 안이다. */
    private static final double WALK_STEP = 0.016;

    /** 간격 0.005도는 약 0.46km 로, 세 구간을 걸어도 도보 시간 임계(90분)에 못 미친다. */
    private static final double SHORT_STEP = 0.005;

    private static SchedulingPlace place(int index, double step) {
        String contentId = "c" + index;
        return new SchedulingPlace(contentId, "장소" + index, null,
                35.0, 127.0 + index * step, OperatingHours.unknown(), 60, false);
    }

    private static List<SchedulingPlace> line(int count, double step) {
        List<SchedulingPlace> places = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            places.add(place(i, step));
        }
        return places;
    }

    /** 인덱스별 고도(m)를 담은 대중교통 컨텍스트. 담기지 않은 지점은 고도 미상이다. */
    private static SchedulingContext transit(double step, Map<Integer, Double> metersByIndex) {
        Map<String, Double> profile = new LinkedHashMap<>();
        metersByIndex.forEach((index, meters) ->
                profile.put(ElevationProfile.key(35.0, 127.0 + index * step), meters));
        return new SchedulingContext(
                TravelMode.TRANSIT, TravelMatrix.empty(), null, new ElevationProfile(profile));
    }

    @Test
    @DisplayName("누적 도보 시간이 임계를 넘으면 그 스톱 뒤를 휴식 지점으로 잡는다.")
    void findPointByWalkTime() {
        // given - 한 구간 약 33분, 세 구간이면 99분으로 임계(90분)를 넘는다.
        List<SchedulingPlace> places = line(5, WALK_STEP);
        SchedulingContext context = transit(WALK_STEP, Map.of());

        // when
        List<RestBreaks.Point> points = RestBreaks.findPoints(places, context);

        // then
        assertThat(points).hasSize(1);
        assertThat(points.get(0).afterContentId()).isEqualTo("c3");
        assertThat(points.get(0).reason()).isEqualTo(WalkEffort.RestReason.WALK_TIME);
    }

    @Test
    @DisplayName("누적 상승고도가 임계를 넘으면 도보 시간이 짧아도 휴식 지점을 잡는다.")
    void findPointByClimb() {
        // given - 구간마다 150m 상승. 두 구간이면 300m 로 임계(200m)를 넘고, 도보 시간은 임계 미만이다.
        List<SchedulingPlace> places = line(4, SHORT_STEP);
        SchedulingContext context = transit(SHORT_STEP, Map.of(0, 0.0, 1, 150.0, 2, 300.0, 3, 300.0));

        // when
        List<RestBreaks.Point> points = RestBreaks.findPoints(places, context);

        // then
        assertThat(points).hasSize(1);
        assertThat(points.get(0).afterContentId()).isEqualTo("c2");
        assertThat(points.get(0).reason()).isEqualTo(WalkEffort.RestReason.CLIMB);
    }

    @Test
    @DisplayName("임계에 못 미치면 휴식 지점을 잡지 않는다.")
    void noPointBelowThreshold() {
        // given
        List<SchedulingPlace> places = line(4, SHORT_STEP);
        SchedulingContext context = transit(SHORT_STEP, Map.of());

        // when
        List<RestBreaks.Point> points = RestBreaks.findPoints(places, context);

        // then
        assertThat(points).isEmpty();
    }

    @Test
    @DisplayName("한 일차에 부담이 두 번 쌓이면 휴식 지점도 두 번 잡는다(누적값 리셋).")
    void findTwoPointsInOneDay() {
        // given - 구간 33분씩. 3구간마다 임계를 넘으므로 8개 스톱이면 두 번 잡힌다.
        List<SchedulingPlace> places = line(8, WALK_STEP);
        SchedulingContext context = transit(WALK_STEP, Map.of());

        // when
        List<RestBreaks.Point> points = RestBreaks.findPoints(places, context);

        // then
        assertThat(points).extracting(RestBreaks.Point::afterContentId).containsExactly("c3", "c6");
    }

    @Test
    @DisplayName("스톱이 0·1·2개면 끼워 넣을 자리가 없어 휴식 지점을 잡지 않는다.")
    void noPointWhenTooFewStops() {
        // given
        SchedulingContext context = transit(WALK_STEP, Map.of());

        // when & then
        assertThat(RestBreaks.findPoints(null, context)).isEmpty();
        assertThat(RestBreaks.findPoints(List.of(), context)).isEmpty();
        assertThat(RestBreaks.findPoints(line(1, WALK_STEP), context)).isEmpty();
        assertThat(RestBreaks.findPoints(line(2, WALK_STEP), context)).isEmpty();
    }

    @Test
    @DisplayName("자동차 구간은 도보가 아니라 휴식 지점을 잡지 않는다.")
    void noPointForCarMode() {
        // given
        List<SchedulingPlace> places = line(8, WALK_STEP);

        // when
        List<RestBreaks.Point> points = RestBreaks.findPoints(places, SchedulingContext.car(null));

        // then
        assertThat(points).isEmpty();
    }

    @Test
    @DisplayName("휴식 스톱을 끼워 넣으면 뒤따르는 스톱의 방문 시각이 밀린다.")
    void insertShiftsFollowingVisitTimes() {
        // given
        List<SchedulingPlace> places = line(5, WALK_STEP);
        SchedulingContext context = transit(WALK_STEP, Map.of());
        ScheduledDay day = DayScheduler.build(1, DATE, places, Map.of(), context);
        SchedulingPlace cafe = new SchedulingPlace("cafe", "쉼표카페", 39,
                35.0, 127.0 + 3 * WALK_STEP, OperatingHours.unknown(), RestBreaks.REST_STAY_MINUTES, false);

        // when
        ScheduledDay inserted = RestBreaks.insert(day, places, context, List.of(
                new RestBreaks.Insertion("c3", cafe, WalkEffort.RestReason.WALK_TIME)));

        // then
        assertThat(inserted.stops()).extracting(ScheduledStop::contentId)
                .containsExactly("c0", "c1", "c2", "c3", "cafe", "c4");
        assertThat(inserted.stops()).extracting(ScheduledStop::order).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(inserted.stops().get(4).autoRest()).isTrue();
        assertThat(inserted.stops().get(4).reason()).contains("도보 이동이 길어져");
        // 휴식 앞 스톱은 그대로, 뒤 스톱은 최소한 휴식 체류시간만큼 밀린다.
        assertThat(inserted.stops().get(3).startTime()).isEqualTo(day.stops().get(3).startTime());
        assertThat(inserted.stops().get(5).startTime())
                .isAfter(day.stops().get(4).startTime().plusMinutes(RestBreaks.REST_STAY_MINUTES - 1));
    }

    @Test
    @DisplayName("채울 휴식 지점이 없으면 원본 일차를 그대로 돌려준다.")
    void insertWithoutInsertionsKeepsDay() {
        // given
        List<SchedulingPlace> places = line(5, WALK_STEP);
        SchedulingContext context = transit(WALK_STEP, Map.of());
        ScheduledDay day = DayScheduler.build(1, DATE, places, Map.of(), context);

        // when
        ScheduledDay inserted = RestBreaks.insert(day, places, context, List.of());

        // then
        assertThat(inserted).isSameAs(day);
    }
}
