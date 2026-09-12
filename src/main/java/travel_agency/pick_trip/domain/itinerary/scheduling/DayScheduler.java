package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 하루치 장소 목록을 실제 방문 가능한 순서·시각으로 확정한다.
 * AI 가 준 순서는 영업시간·이동거리를 모르고 만든 초안이므로, 여기서 제약을 반영해 재배치한다.
 */
public final class DayScheduler {

    /** LocalTime 으로 표현 가능한 마지막 분(23:59). 이 값을 넘기면 자정을 돌아 다음 날 새벽으로 뒤집힌다. */
    private static final int MAX_MINUTE_OF_DAY = 1439;

    private static final int DAY_START_MINUTE = SchedulingPolicy.DAY_START.getHour() * 60
            + SchedulingPolicy.DAY_START.getMinute();
    private static final int DAY_SOFT_END_MINUTE = SchedulingPolicy.DAY_SOFT_END.getHour() * 60
            + SchedulingPolicy.DAY_SOFT_END.getMinute();

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 2-opt 개선 패스 상한. 한 패스가 O(n^2) 번의 시뮬레이션이라 상한이 없으면 응답 시간을 보장할 수 없다.
     * 하루 장소가 수십 개인 소도시 일정에서는 보통 서너 패스 안에 더 나아지지 않으므로 20이면 충분히 여유롭다.
     */
    private static final int MAX_TWO_OPT_PASSES = 20;

    private DayScheduler() {
    }

    /**
     * @param context 이동수단·도로 행렬·시작 지점. 시작 지점은 이 일차의 첫 스톱으로 고정하며,
     *                이 일차에 없으면 무시한다.
     */
    public static ScheduledDay schedule(int dayIndex,
                                        LocalDate date,
                                        List<SchedulingPlace> places,
                                        Map<String, String> reasonByContentId,
                                        SchedulingContext context) {
        List<SchedulingPlace> source = (places == null)
                ? List.of()
                : places.stream().filter(Objects::nonNull).toList();
        if (source.isEmpty()) {
            return new ScheduledDay(dayIndex, date, List.of(), 0, 0.0, List.of());
        }

        return build(dayIndex, date, bestOrder(source, context), reasonByContentId, context);
    }

    /**
     * 순서가 이미 확정된 목록에 시각·이동 요약을 배정한다.
     * 순서 탐색을 건너뛰므로, 확정된 순서에 휴식 스톱을 끼워 넣은 뒤 다시 시각을 매기는 데도 쓴다
     * ({@link RestBreaks}). 여기서 다시 순서를 바꾸면 끼워 넣은 위치가 흐트러진다.
     */
    static ScheduledDay build(int dayIndex,
                              LocalDate date,
                              List<SchedulingPlace> ordered,
                              Map<String, String> reasonByContentId,
                              SchedulingContext context) {
        Map<String, String> reasons = (reasonByContentId == null) ? Map.of() : reasonByContentId;
        Simulation sim = simulate(ordered, context);

        List<ScheduledStop> stops = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            SchedulingPlace place = ordered.get(i);
            Visit visit = sim.visits().get(i);
            stops.add(new ScheduledStop(
                    place.contentId(),
                    place.title(),
                    i + 1,
                    reasons.get(place.contentId()),
                    toTime(visit.arrival()),
                    toTime(visit.departure()),
                    visit.notes()));
        }

        List<String> dayNotes = new ArrayList<>(sim.hopWarnings());
        if (sim.totalTravelMinutes() > SchedulingPolicy.MAX_DAY_TRAVEL_MINUTES) {
            dayNotes.add("하루 총 이동시간이 약 %d시간 %d분입니다."
                    .formatted(sim.totalTravelMinutes() / 60, sim.totalTravelMinutes() % 60));
        }
        if (sim.clamped()) {
            dayNotes.add("하루 일정이 너무 길어 시각이 잘렸습니다. 장소를 줄이거나 일차를 늘려보세요.");
        }
        if (sim.visits().get(sim.visits().size() - 1).departure() > DAY_SOFT_END_MINUTE) {
            dayNotes.add("일정이 21:00을 넘깁니다.");
        }

        return new ScheduledDay(
                dayIndex,
                date,
                stops,
                sim.totalTravelMinutes(),
                Math.round(sim.totalTravelKm() * 100) / 100.0,
                dayNotes);
    }

    /**
     * 최선의 방문 순서를 고른다.
     * 시작 지점이 이 일차에 있으면 첫 자리에 고정하고 나머지 자리만 탐색한다.
     * 7개 이하는 전순열로 전역 최적을, 8개 이상은 nearest-neighbor 초기해 + 2-opt 로 근사한다.
     */
    private static List<SchedulingPlace> bestOrder(List<SchedulingPlace> source, SchedulingContext context) {
        String anchorContentId = context.startContentId();
        int anchorIndex = indexOf(source, anchorContentId);
        List<SchedulingPlace> ordered = (anchorIndex <= 0) ? source : moveToFront(source, anchorIndex);
        // 앵커가 있으면 0번 자리는 탐색 대상에서 뺀다.
        int fixed = (anchorIndex >= 0) ? 1 : 0;
        if (ordered.size() - fixed <= 1) {
            return ordered;
        }

        if (ordered.size() <= SchedulingPolicy.MAX_REORDER_STOPS) {
            return bestByPermutation(ordered, fixed, context);
        }

        // 8개부터는 순열 수가 4만을 넘어 요청 응답 시간 안에 감당할 수 없다.
        // 대신 좌표 기반 nearest-neighbor 로 초기해를 만들고 2-opt 로 다듬는다.
        // ponytail: 초기해 하나에서만 2-opt 를 돌려 지역 최적에 갇힐 수 있다.
        // 하루 8개 이상은 드문 입력이라 이 정도로 두고, 실제 품질 불만이 나오면 or-opt·다중 시작점을 얹는다.
        List<SchedulingPlace> initial = RouteOptimizer.nearestNeighborOrder(ordered, context);
        if (fixed == 1) {
            // 앵커에 좌표가 없으면 nearest-neighbor 가 뒤로 밀어버리므로 첫 자리로 되돌린다.
            initial = moveToFront(initial, indexOf(initial, anchorContentId));
        }
        return twoOpt(initial, fixed, context);
    }

    /**
     * 전순열을 (하드 위반 수, 총 이동분) 으로 평가해 최선의 순서를 고른다.
     * 항등 순열부터 사전식으로 탐색하고 개선이 있을 때만 교체하므로, 동점이면 AI 원안이 남는다.
     */
    private static List<SchedulingPlace> bestByPermutation(List<SchedulingPlace> ordered, int fixed,
                                                           SchedulingContext context) {
        int n = ordered.size();
        int[] indexes = new int[n - fixed];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = fixed + i;
        }

        List<SchedulingPlace> best = ordered;
        Simulation bestSim = simulate(ordered, context);
        while (nextPermutation(indexes)) {
            List<SchedulingPlace> candidate = new ArrayList<>(n);
            for (int i = 0; i < fixed; i++) {
                candidate.add(ordered.get(i));
            }
            for (int index : indexes) {
                candidate.add(ordered.get(index));
            }
            Simulation sim = simulate(candidate, context);
            if (isBetter(sim, bestSim)) {
                best = candidate;
                bestSim = sim;
            }
        }
        return best;
    }

    /**
     * 구간을 뒤집어(2-opt) 더 나은 순서를 찾는다. 평가는 전순열 경로와 같은 simulate/isBetter 를 쓴다.
     * 개선이 없으면 즉시 멈추고, 그렇지 않아도 {@link #MAX_TWO_OPT_PASSES} 패스에서 끊는다.
     */
    private static List<SchedulingPlace> twoOpt(List<SchedulingPlace> initial, int fixed,
                                               SchedulingContext context) {
        List<SchedulingPlace> best = initial;
        Simulation bestSim = simulate(best, context);

        for (int pass = 0; pass < MAX_TWO_OPT_PASSES; pass++) {
            boolean improved = false;
            for (int i = fixed; i < best.size() - 1; i++) {
                for (int j = i + 1; j < best.size(); j++) {
                    List<SchedulingPlace> candidate = reversed(best, i, j);
                    Simulation sim = simulate(candidate, context);
                    if (isBetter(sim, bestSim)) {
                        best = candidate;
                        bestSim = sim;
                        improved = true;
                    }
                }
            }
            if (!improved) {
                break;
            }
        }
        return best;
    }

    /** [from, to] 구간만 뒤집은 새 리스트. */
    private static List<SchedulingPlace> reversed(List<SchedulingPlace> source, int from, int to) {
        List<SchedulingPlace> result = new ArrayList<>(source);
        for (int left = from, right = to; left < right; left++, right--) {
            SchedulingPlace tmp = result.get(left);
            result.set(left, result.get(right));
            result.set(right, tmp);
        }
        return result;
    }

    private static List<SchedulingPlace> moveToFront(List<SchedulingPlace> source, int index) {
        List<SchedulingPlace> result = new ArrayList<>(source.size());
        result.add(source.get(index));
        for (int i = 0; i < source.size(); i++) {
            if (i != index) {
                result.add(source.get(i));
            }
        }
        return result;
    }

    /** 없으면 -1. contentId 가 null 이면(= 시작 지점 미지정) 탐색하지 않는다. */
    private static int indexOf(List<SchedulingPlace> source, String contentId) {
        if (contentId == null) {
            return -1;
        }
        for (int i = 0; i < source.size(); i++) {
            if (contentId.equals(source.get(i).contentId())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBetter(Simulation candidate, Simulation best) {
        if (candidate.hardViolations() != best.hardViolations()) {
            return candidate.hardViolations() < best.hardViolations();
        }
        return candidate.totalTravelMinutes() < best.totalTravelMinutes();
    }

    /** 사전식 다음 순열로 제자리 변경한다. 마지막 순열이면 false. */
    private static boolean nextPermutation(int[] a) {
        int i = a.length - 2;
        while (i >= 0 && a[i] >= a[i + 1]) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        int j = a.length - 1;
        while (a[j] <= a[i]) {
            j--;
        }
        swap(a, i, j);
        for (int left = i + 1, right = a.length - 1; left < right; left++, right--) {
            swap(a, left, right);
        }
        return true;
    }

    private static void swap(int[] a, int i, int j) {
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    /** 순서 평가와 최종 시각 배정이 어긋나지 않도록 두 경로 모두 이 시뮬레이션 하나만 사용한다. */
    private static Simulation simulate(List<SchedulingPlace> ordered, SchedulingContext context) {
        List<Visit> visits = new ArrayList<>(ordered.size());
        List<String> hopWarnings = new ArrayList<>();
        int hardViolations = 0;
        int totalTravelMinutes = 0;
        double totalTravelKm = 0.0;
        boolean clamped = false;
        int cursor = DAY_START_MINUTE;

        for (int i = 0; i < ordered.size(); i++) {
            SchedulingPlace place = ordered.get(i);
            OperatingHours hours = place.operatingHours();
            List<String> notes = new ArrayList<>();

            int arrival = cursor;
            Integer open = hours.openMinuteOfDay();
            if (open != null && cursor < open) {
                arrival = open;
                notes.add("개장 전 도착이라 %s까지 대기가 필요합니다.".formatted(formatMinute(open)));
            }

            int departure = arrival + place.stayMinutes();
            Integer close = hours.closeMinuteOfDay();
            if (close != null && departure > close) {
                hardViolations++;
                notes.add("운영시간(~%s)을 넘겨 관람이 어려울 수 있습니다.".formatted(formatMinute(close)));
            }

            // 계산은 분 단위 정수로 하고 여기서만 잘라내, LocalTime 이 자정을 넘어 새벽으로 뒤집히는 일을 막는다.
            if (arrival > MAX_MINUTE_OF_DAY) {
                arrival = MAX_MINUTE_OF_DAY;
                clamped = true;
            }
            if (departure > MAX_MINUTE_OF_DAY) {
                departure = MAX_MINUTE_OF_DAY;
                clamped = true;
            }
            visits.add(new Visit(arrival, departure, List.copyOf(notes)));
            cursor = departure;

            if (i + 1 < ordered.size()) {
                SchedulingPlace next = ordered.get(i + 1);
                TravelMatrix.Leg hop = context.legBetween(place, next);
                int hopMinutes;
                if (hop != null) {
                    hopMinutes = hop.minutes();
                    totalTravelKm += hop.km();
                    if (hop.km() > SchedulingPolicy.MAX_SINGLE_HOP_KM) {
                        hopWarnings.add("'%s'에서 '%s'까지 약 %dkm로 이동 부담이 큽니다."
                                .formatted(place.title(), next.title(), Math.round(hop.km())));
                    }
                } else {
                    // 좌표가 없으면 거리를 추정할 근거가 없어 고정값만 쓰고 거리 통계·경고에서는 제외한다.
                    hopMinutes = SchedulingPolicy.UNKNOWN_HOP_MINUTES;
                }
                totalTravelMinutes += hopMinutes;
                cursor = departure + hopMinutes;
            }
        }

        return new Simulation(visits, hardViolations, totalTravelMinutes, totalTravelKm, clamped, hopWarnings);
    }

    private static LocalTime toTime(int minuteOfDay) {
        return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
    }

    private static String formatMinute(int minuteOfDay) {
        return toTime(Math.min(minuteOfDay, MAX_MINUTE_OF_DAY)).format(HH_MM);
    }

    /** 한 장소의 도착·출발 시각(분)과 그 장소에 붙은 안내. */
    private record Visit(int arrival, int departure, List<String> notes) {
    }

    private record Simulation(
            List<Visit> visits,
            int hardViolations,
            int totalTravelMinutes,
            double totalTravelKm,
            boolean clamped,
            List<String> hopWarnings
    ) {
    }
}
