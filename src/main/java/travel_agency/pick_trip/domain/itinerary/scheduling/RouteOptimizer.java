package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 시작 지점을 앵커로 삼아 거리 기반 동선을 만든다.
 * 외부 의존성 없이 값만 보는 순수 코드이며, 일차 재배분({@link #redistribute})과
 * 하루 안의 초기 순서({@link #nearestNeighborOrder})가 같은 nearest-neighbor 규칙을 쓴다.
 *
 * <p>구간 거리는 {@link SchedulingContext#legBetween}에서 가져온다. 도로 행렬이 있으면 실제 도로 거리,
 * 없으면 직선거리다.
 * ponytail: 한 줄 경로는 거리 기준으로만 꿴다. 대중교통은 도보/버스 경계에서 거리 순서와 시간 순서가
 * 어긋날 수 있으나, 하루 안 순서는 뒤에서 시간 기준으로 다시 최적화하므로 일차 배분에만 영향이 남는다.
 */
public final class RouteOptimizer {

    /**
     * 두 후보의 거리 차가 이 값(1m) 이하면 사실상 같은 거리로 보고 "꼭 가기"를 먼저 잡는다.
     * haversine 결과를 그대로 비교하면 부동소수 오차 때문에 동률이 거의 성립하지 않는다.
     */
    private static final double TIE_KM = 0.001;

    private RouteOptimizer() {
    }

    /**
     * AI 가 나눈 일차 배분을 초기값으로 받아, 전체 장소를 앵커에서 출발하는 한 줄 경로로 다시 꿴 뒤
     * 일수만큼 순서대로 잘라 배분한다.
     *
     * <p>한 줄로 꿰기 때문에 지리적으로 인접한 장소가 자연히 같은 일차에 묶이고,
     * 각 일차의 첫 장소는 전날 마지막 장소에서 가장 가까운 곳이 된다(= 전날 마지막 스톱이 다음 날 앵커).
     * 별도의 군집화(k-means) 없이도 "동선"이라는 목적에 직접 맞는다.
     *
     * <p>일차 수는 입력({@code daysPlan})을 그대로 따른다. 사용자가 요청한 여행 길이를 바꾸지 않기 위함이다.
     *
     * @param context 이동수단·도로 행렬·시작 지점. 시작 지점이 null 이거나 배분 대상에 없으면
     *                첫 좌표 보유 장소에서 출발한다.
     * @return 새 일차별 contentId 배열 (일차 수는 입력과 동일)
     */
    public static List<List<String>> redistribute(List<List<String>> daysPlan,
                                                  Map<String, SchedulingPlace> places,
                                                  SchedulingContext context) {
        if (daysPlan == null || daysPlan.isEmpty()) {
            return daysPlan;
        }

        // filterToBasket 을 이미 통과한 입력이지만, 이 클래스만 따로 쓰는 호출을 대비해 여기서도 중복을 걷어낸다.
        Set<String> flat = new LinkedHashSet<>();
        for (List<String> day : daysPlan) {
            if (day != null) {
                day.stream().filter(contentId -> contentId != null && places.get(contentId) != null)
                        .forEach(flat::add);
            }
        }
        if (flat.isEmpty()) {
            return daysPlan;
        }

        List<SchedulingPlace> route = nearestNeighborOrder(flat.stream().map(places::get).toList(), context);

        // 균등 분할하고 나머지는 앞 일차부터 하나씩 더 준다. 장소 수가 일수보다 적으면 뒤쪽 일차는 비게 된다.
        // ponytail: 개수로만 균등 분할해 체류시간·운영시간이 긴 장소가 한 일차에 몰릴 수 있다.
        // 하루 종료 시각 경고가 특정 일차에 반복해서 붙으면 체류시간 합 기준 분할로 올린다.
        int dayCount = daysPlan.size();
        int base = route.size() / dayCount;
        int remainder = route.size() % dayCount;

        List<List<String>> result = new ArrayList<>(dayCount);
        int cursor = 0;
        for (int day = 0; day < dayCount; day++) {
            int size = base + (day < remainder ? 1 : 0);
            List<String> assigned = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                assigned.add(route.get(cursor++).contentId());
            }
            result.add(assigned);
        }
        return result;
    }

    /**
     * 앵커에서 출발해 매번 가장 가까운 장소로 이동하는 nearest-neighbor 순서를 만든다.
     * 거리 차가 {@link #TIE_KM} 이내로 사실상 동률이면 "꼭 가기"(mustVisit) 장소를 먼저 잡는다.
     *
     * <p>좌표가 없는 장소는 거리를 잴 근거가 없어 경로 탐색에 넣지 못한다.
     * 원래 순서를 유지한 채 경로 뒤에 그대로 이어 붙인다.
     *
     * @param context 이동수단·도로 행렬·시작 지점. 시작 지점이 null 이거나 목록에 없거나 좌표가 없으면
     *                좌표를 가진 첫 장소에서 출발한다.
     */
    public static List<SchedulingPlace> nearestNeighborOrder(List<SchedulingPlace> places,
                                                             SchedulingContext context) {
        if (places == null || places.size() <= 1) {
            return places == null ? List.of() : List.copyOf(places);
        }

        List<SchedulingPlace> withCoordinates = new ArrayList<>();
        List<SchedulingPlace> withoutCoordinates = new ArrayList<>();
        for (SchedulingPlace place : places) {
            (place.hasCoordinates() ? withCoordinates : withoutCoordinates).add(place);
        }
        if (withCoordinates.isEmpty()) {
            return List.copyOf(places);
        }

        int startIndex = indexOfAnchor(withCoordinates, context.startContentId());
        List<SchedulingPlace> route = new ArrayList<>(places.size());
        boolean[] visited = new boolean[withCoordinates.size()];

        int current = startIndex;
        route.add(withCoordinates.get(current));
        visited[current] = true;

        for (int step = 1; step < withCoordinates.size(); step++) {
            int best = -1;
            double bestKm = Double.MAX_VALUE;
            for (int i = 0; i < withCoordinates.size(); i++) {
                if (visited[i]) {
                    continue;
                }
                double km = kilometers(context, withCoordinates.get(current), withCoordinates.get(i));
                if (best < 0 || km < bestKm - TIE_KM
                        || (km <= bestKm + TIE_KM
                        && withCoordinates.get(i).mustVisit() && !withCoordinates.get(best).mustVisit())) {
                    best = i;
                    bestKm = km;
                }
            }
            visited[best] = true;
            route.add(withCoordinates.get(best));
            current = best;
        }

        route.addAll(withoutCoordinates);
        return route;
    }

    private static int indexOfAnchor(List<SchedulingPlace> places, String anchorContentId) {
        if (anchorContentId == null) {
            return 0;
        }
        for (int i = 0; i < places.size(); i++) {
            if (anchorContentId.equals(places.get(i).contentId())) {
                return i;
            }
        }
        return 0;
    }

    /** 좌표를 가진 장소끼리만 부르므로 구간이 null 로 돌아오지 않는다. */
    private static double kilometers(SchedulingContext context, SchedulingPlace from, SchedulingPlace to) {
        return context.legBetween(from, to).km();
    }
}
