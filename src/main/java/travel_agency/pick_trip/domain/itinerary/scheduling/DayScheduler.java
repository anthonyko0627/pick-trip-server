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

    private DayScheduler() {
    }

    public static ScheduledDay schedule(int dayIndex,
                                        LocalDate date,
                                        List<SchedulingPlace> places,
                                        Map<String, String> reasonByContentId) {
        List<SchedulingPlace> source = (places == null)
                ? List.of()
                : places.stream().filter(Objects::nonNull).toList();
        if (source.isEmpty()) {
            return new ScheduledDay(dayIndex, date, List.of(), 0, 0.0, List.of());
        }

        Map<String, String> reasons = (reasonByContentId == null) ? Map.of() : reasonByContentId;
        List<SchedulingPlace> ordered = bestOrder(source);
        Simulation sim = simulate(ordered);

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
     * 전순열을 (하드 위반 수, 총 이동분) 으로 평가해 최선의 순서를 고른다.
     * 항등 순열부터 사전식으로 탐색하고 개선이 있을 때만 교체하므로, 동점이면 AI 원안이 남는다.
     */
    private static List<SchedulingPlace> bestOrder(List<SchedulingPlace> source) {
        int n = source.size();
        if (n > SchedulingPolicy.MAX_REORDER_STOPS) {
            // 8개부터는 순열 수가 4만을 넘어 요청 응답 시간 안에 감당할 수 없다.
            return source;
        }

        int[] indexes = new int[n];
        for (int i = 0; i < n; i++) {
            indexes[i] = i;
        }

        List<SchedulingPlace> best = source;
        Simulation bestSim = simulate(source);
        while (nextPermutation(indexes)) {
            List<SchedulingPlace> candidate = new ArrayList<>(n);
            for (int index : indexes) {
                candidate.add(source.get(index));
            }
            Simulation sim = simulate(candidate);
            if (isBetter(sim, bestSim)) {
                best = candidate;
                bestSim = sim;
            }
        }
        return best;
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
    private static Simulation simulate(List<SchedulingPlace> ordered) {
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
                int hopMinutes;
                if (place.hasCoordinates() && next.hasCoordinates()) {
                    double km = GeoDistance.kilometers(
                            place.latitude(), place.longitude(), next.latitude(), next.longitude());
                    hopMinutes = TravelTimeEstimator.minutes(km);
                    totalTravelKm += km;
                    if (km > SchedulingPolicy.MAX_SINGLE_HOP_KM) {
                        hopWarnings.add("'%s'에서 '%s'까지 약 %dkm로 이동 부담이 큽니다."
                                .formatted(place.title(), next.title(), Math.round(km)));
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
