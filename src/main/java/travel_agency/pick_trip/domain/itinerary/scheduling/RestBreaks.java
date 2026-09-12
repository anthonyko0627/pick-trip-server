package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 도보 부담이 쌓인 지점을 찾아 휴식 스톱을 끼워 넣는다.
 *
 * <p>순서가 확정된 뒤에만 동작한다. 순서 탐색(전순열·2-opt) 도중에 끼워 넣으면 후보마다 스톱 수가 달라져
 * 평가가 흔들린다.
 *
 * <p>이 패키지는 순수 코드라 근처 카페를 직접 조회하지 못한다. 그래서 두 단계로 나눈다.
 * {@link #findPoints}가 "어느 스톱 뒤에 무슨 이유로" 휴식이 필요한지만 계산해 돌려주고,
 * 서비스 레이어가 실제 콘텐츠(카페)를 채워 {@link #insert}로 되돌려준다.
 * 카페를 못 찾으면 그 지점은 그냥 비우면 되므로 이 클래스가 조회 실패를 알 필요가 없다.
 */
public final class RestBreaks {

    /**
     * 휴식 스톱의 체류 시간(분). 카페에서 음료 한 잔을 주문해 마시고 나오는 데 30분 안팎이 걸린다.
     * 더 길게 잡으면 체력 안배로 넣은 스톱이 되레 남은 일정을 밀어내 관람 시간을 잡아먹는다.
     */
    public static final int REST_STAY_MINUTES = 30;

    private RestBreaks() {
    }

    /** 휴식이 필요한 지점. {@code afterContentId} 스톱 바로 뒤에 끼워 넣는다. */
    public record Point(String afterContentId, WalkEffort.RestReason reason) {
    }

    /** 휴식 지점에 채워 넣을 실제 장소. 서비스 레이어가 근처 카페로 채운다. */
    public record Insertion(String afterContentId, SchedulingPlace place, WalkEffort.RestReason reason) {
    }

    /**
     * 확정된 순서를 훑어 휴식이 필요한 지점을 찾는다.
     * 마지막 휴식 이후의 도보 시간·상승고도를 쌓다가 {@link WalkEffort#restReason} 이 임계를 넘었다고 하면
     * 그 스톱 뒤를 휴식 지점으로 잡고 누적값을 리셋한다(그래서 하루에 여러 번 나올 수 있다).
     *
     * <p>도보로 분류되지 않는 구간(자동차·버스 환산 구간)과 좌표 미상 구간은 누적하지 않는다.
     * 그날 마지막 스톱 뒤에는 넣지 않는다 — 일정이 거기서 끝나므로 쉴 이유가 없다.
     */
    public static List<Point> findPoints(List<SchedulingPlace> ordered, SchedulingContext context) {
        // 3개 미만이면 "마지막이 아닌 스톱 뒤"라는 자리가 나오지 않는다.
        if (ordered == null || ordered.size() < 3 || context == null) {
            return List.of();
        }

        List<Point> points = new ArrayList<>();
        int walkMinutes = 0;
        double climbMeters = 0.0;
        int last = ordered.size() - 1;

        for (int i = 0; i < last; i++) {
            SchedulingPlace from = ordered.get(i);
            SchedulingPlace to = ordered.get(i + 1);
            TravelMatrix.Leg leg = context.legBetween(from, to);
            if (leg != null && TravelTimeEstimator.isWalkLeg(leg.km(), context.travelMode())) {
                // leg.minutes() 에는 경사 페널티가 이미 포함돼 있다. 오르막을 걷는 시간도 도보 부담이다.
                walkMinutes += leg.minutes();
                climbMeters += WalkEffort.climbMeters(
                        context.elevationMetersAt(from), context.elevationMetersAt(to));
            }
            if (i + 1 == last) {
                break;
            }
            WalkEffort.RestReason reason = WalkEffort.restReason(walkMinutes, climbMeters);
            if (reason.needsRest()) {
                points.add(new Point(to.contentId(), reason));
                walkMinutes = 0;
                climbMeters = 0.0;
            }
        }
        return List.copyOf(points);
    }

    /**
     * 휴식 스톱을 끼워 넣고 시각·이동 요약을 다시 매긴다.
     * 뒤따르는 스톱의 방문 시각은 휴식 체류·왕복 이동만큼 자동으로 밀린다
     * ({@link DayScheduler#build} 가 같은 시뮬레이션으로 전부 다시 배정하기 때문이다).
     *
     * @param ordered    {@code day.stops()} 와 같은 순서의 장소 목록
     * @param insertions 채워진 휴식 지점. 비어 있으면 원본을 그대로 돌려준다.
     */
    public static ScheduledDay insert(ScheduledDay day,
                                      List<SchedulingPlace> ordered,
                                      SchedulingContext context,
                                      List<Insertion> insertions) {
        if (day == null || insertions == null || insertions.isEmpty()) {
            return day;
        }

        Map<String, Insertion> byAfterContentId = new HashMap<>();
        insertions.forEach(insertion -> byAfterContentId.putIfAbsent(insertion.afterContentId(), insertion));

        Map<String, String> reasons = new LinkedHashMap<>();
        day.stops().forEach(stop -> reasons.put(stop.contentId(), stop.reason()));

        List<SchedulingPlace> merged = new ArrayList<>(ordered.size() + insertions.size());
        Set<String> restContentIds = new HashSet<>();
        for (SchedulingPlace place : ordered) {
            merged.add(place);
            Insertion insertion = byAfterContentId.get(place.contentId());
            if (insertion != null && insertion.place() != null) {
                merged.add(insertion.place());
                reasons.put(insertion.place().contentId(), message(insertion.reason()));
                restContentIds.add(insertion.place().contentId());
            }
        }
        if (restContentIds.isEmpty()) {
            return day;
        }

        ScheduledDay rebuilt = DayScheduler.build(
                day.dayIndex(), day.date(), merged, reasons, context);
        return withRestFlags(rebuilt, day, restContentIds);
    }

    /**
     * 재계산된 하루에 휴식 스톱 표시를 달고, 재계산으로는 되살아나지 않는 안내
     * (휴무일 안내처럼 스케줄러 바깥에서 붙인 것)를 원본에서 되가져온다.
     */
    private static ScheduledDay withRestFlags(ScheduledDay rebuilt, ScheduledDay original,
                                              Set<String> restContentIds) {
        Map<String, List<String>> originalNotes = new HashMap<>();
        original.stops().forEach(stop -> originalNotes.put(stop.contentId(), stop.notes()));

        List<ScheduledStop> stops = rebuilt.stops().stream()
                .map(stop -> {
                    List<String> notes = new ArrayList<>(stop.notes());
                    originalNotes.getOrDefault(stop.contentId(), List.of()).stream()
                            .filter(note -> !notes.contains(note))
                            .forEach(notes::add);
                    return new ScheduledStop(stop.contentId(), stop.title(), stop.order(), stop.reason(),
                            stop.startTime(), stop.endTime(), notes,
                            restContentIds.contains(stop.contentId()));
                })
                .toList();
        return new ScheduledDay(rebuilt.dayIndex(), rebuilt.date(), stops,
                rebuilt.totalTravelMinutes(), rebuilt.totalTravelKm(), rebuilt.dayNotes());
    }

    /** 삽입 이유 문구. 사용자가 "왜 여기에 카페가 끼었는지" 를 바로 알 수 있게 사유별로 나눈다. */
    private static String message(WalkEffort.RestReason reason) {
        return switch (reason) {
            case WALK_TIME -> "도보 이동이 길어져 잠시 쉬어가도록 넣은 휴식 장소입니다.";
            case CLIMB -> "오르막이 이어져 체력 안배를 위해 넣은 휴식 장소입니다.";
            case BOTH -> "도보 이동이 길고 오르막도 이어져 체력 안배를 위해 넣은 휴식 장소입니다.";
            case NONE -> "체력 안배를 위해 넣은 휴식 장소입니다.";
        };
    }
}
