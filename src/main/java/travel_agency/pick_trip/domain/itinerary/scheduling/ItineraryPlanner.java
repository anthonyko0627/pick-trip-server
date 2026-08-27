package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 가 제안한 일차별 장소 배열을 실제 여행 가능한 일정으로 확정한다.
 * 바구니 화이트리스트 검증 → 휴무일 재배치 → 일차별 시각 배정 순으로 처리한다.
 */
public final class ItineraryPlanner {

    private static final String NO_TRAVEL_DATE_MESSAGE = "여행 날짜가 없어 휴무일 검증을 건너뛰었습니다.";
    private static final String CLOSED_STOP_NOTE = "방문 예정일이 휴무일입니다.";

    /** DayOfWeek.getDisplayName 은 JVM locale provider 설정에 좌우되므로 직접 고정한다. */
    private static final String[] KOREAN_DAY_NAMES = {
            "", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"
    };

    private ItineraryPlanner() {
    }

    public static PlannedItinerary plan(String title,
                                        List<List<String>> dayContentIds,
                                        Map<String, SchedulingPlace> placesById,
                                        Map<String, String> reasonByContentId,
                                        LocalDate travelDate) {
        List<List<String>> requested = (dayContentIds == null) ? List.of() : dayContentIds;
        Map<String, SchedulingPlace> places = (placesById == null) ? Map.of() : placesById;
        Map<String, String> reasons = (reasonByContentId == null) ? Map.of() : reasonByContentId;

        List<String> adjustments = new ArrayList<>();
        List<List<String>> daysPlan = filterToBasket(requested, places);

        // 휴무일이지만 옮길 일차가 없어 그대로 둔 장소. 스케줄링 후 안내를 덧붙이기 위해 모아둔다.
        Set<String> stillClosed = new HashSet<>();
        if (travelDate == null) {
            adjustments.add(NO_TRAVEL_DATE_MESSAGE);
        } else {
            moveClosedStops(daysPlan, places, travelDate, adjustments, stillClosed);
        }

        List<ScheduledDay> days = new ArrayList<>(daysPlan.size());
        for (int i = 0; i < daysPlan.size(); i++) {
            List<SchedulingPlace> dayPlaces = daysPlan.get(i).stream().map(places::get).toList();
            ScheduledDay day = DayScheduler.schedule(
                    i + 1,
                    travelDate == null ? null : travelDate.plusDays(i),
                    dayPlaces,
                    reasons);
            days.add(stillClosed.isEmpty() ? day : withClosedNotes(day, stillClosed));
        }

        return new PlannedItinerary(title, days, adjustments);
    }

    /**
     * 바구니(placesById)에 없는 contentId 를 걷어낸다. AI 가 지어낸 장소와 중복 배치를 여기서 모두 제거한다.
     * 일차 수는 그대로 유지해 사용자가 요청한 여행 길이를 바꾸지 않는다.
     */
    private static List<List<String>> filterToBasket(List<List<String>> requested,
                                                     Map<String, SchedulingPlace> places) {
        Set<String> used = new HashSet<>();
        List<List<String>> result = new ArrayList<>(requested.size());
        for (List<String> day : requested) {
            List<String> kept = new ArrayList<>();
            if (day != null) {
                for (String contentId : day) {
                    // containsKey 가 아니라 get 으로 값까지 확인한다. 값이 null 인 엔트리를 통과시키면
                    // 뒤의 휴무일 패스에서 NPE 가 난다.
                    if (contentId != null && places.get(contentId) != null && used.add(contentId)) {
                        kept.add(contentId);
                    }
                }
            }
            result.add(kept);
        }
        return result;
    }

    private static void moveClosedStops(List<List<String>> daysPlan,
                                        Map<String, SchedulingPlace> places,
                                        LocalDate travelDate,
                                        List<String> adjustments,
                                        Set<String> stillClosed) {
        for (int day = 0; day < daysPlan.size(); day++) {
            DayOfWeek dayOfWeek = travelDate.plusDays(day).getDayOfWeek();
            // 순회 중 원본 리스트에서 제거하므로 복사본을 돈다.
            for (String contentId : List.copyOf(daysPlan.get(day))) {
                SchedulingPlace place = places.get(contentId);
                if (!place.operatingHours().isClosedOn(dayOfWeek)) {
                    continue;
                }
                int target = findOpenDay(daysPlan.size(), day, travelDate, place);
                if (target < 0) {
                    stillClosed.add(contentId);
                    continue;
                }
                daysPlan.get(day).remove(contentId);
                daysPlan.get(target).add(contentId);
                adjustments.add("'%s'은 %d일차(%s)에 휴무여서 %d일차로 옮겼습니다."
                        .formatted(place.title(), day + 1, KOREAN_DAY_NAMES[dayOfWeek.getValue()], target + 1));
            }
        }
    }

    /** 현재 일차에서 가장 가까운(동점이면 앞선) 영업일 인덱스. 없으면 -1. */
    private static int findOpenDay(int dayCount, int from, LocalDate travelDate, SchedulingPlace place) {
        for (int distance = 1; distance < dayCount; distance++) {
            for (int candidate : new int[]{from - distance, from + distance}) {
                if (candidate < 0 || candidate >= dayCount) {
                    continue;
                }
                if (!place.operatingHours().isClosedOn(travelDate.plusDays(candidate).getDayOfWeek())) {
                    return candidate;
                }
            }
        }
        return -1;
    }

    /**
     * DayScheduler 는 휴무 여부를 알지 못하므로, 결과를 받은 뒤 해당 스톱에만 안내를 덧붙인다.
     */
    private static ScheduledDay withClosedNotes(ScheduledDay day, Set<String> stillClosed) {
        if (day.stops().stream().noneMatch(stop -> stillClosed.contains(stop.contentId()))) {
            return day;
        }
        List<ScheduledStop> stops = day.stops().stream()
                .map(stop -> {
                    if (!stillClosed.contains(stop.contentId())) {
                        return stop;
                    }
                    List<String> notes = new ArrayList<>(stop.notes());
                    notes.add(CLOSED_STOP_NOTE);
                    return new ScheduledStop(stop.contentId(), stop.title(), stop.order(), stop.reason(),
                            stop.startTime(), stop.endTime(), notes);
                })
                .toList();
        return new ScheduledDay(day.dayIndex(), day.date(), stops,
                day.totalTravelMinutes(), day.totalTravelKm(), day.dayNotes());
    }
}
