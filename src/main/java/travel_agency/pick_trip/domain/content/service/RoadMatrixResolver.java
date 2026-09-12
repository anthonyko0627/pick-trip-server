package travel_agency.pick_trip.domain.content.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.KakaoMobilityClient;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestRequest;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestResponse;
import travel_agency.pick_trip.domain.itinerary.scheduling.GeoDistance;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPlace;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPolicy;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMatrix;

/**
 * 일정에 들어갈 장소들 사이의 실제 자동차 도로 거리·소요 시간 행렬을 Kakao Mobility 길찾기로 만든다.
 *
 * <p>여러 목적지 길찾기는 1 origin → 최대 30 목적지를 1콜로 처리하므로, 장소 s개의 전체 행렬은
 * origin 당 1콜씩 총 s콜이면 된다. 인접 구간만 뽑는 것과 콜 수가 거의 같으면서 순서 최적화에도 쓸 수 있다.
 *
 * <p><b>한계</b>: 요청의 {@code radius} 상한이 10km 라, 기준점에서 그보다 먼 목적지는 응답에서 빠진다.
 * 빠진 구간은 이 행렬에 담기지 않고 스케줄러가 직선거리 환산으로 조용히 폴백한다.
 *
 * <p>길찾기 실패·타임아웃은 예외로 올리지 않는다. 경고만 남기고 빈 행렬 또는 부분 행렬을 반환하며,
 * 재시도하지 않는다(사용량 절약).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoadMatrixResolver {

    /** Kakao Mobility 여러 목적지 길찾기 {@code radius} 상한(m). */
    private static final int KAKAO_RADIUS_MAX_METERS = 10_000;

    /**
     * 한 번의 일정 생성에서 실측할 장소 수 상한(= 콜 수 상한. origin 당 1콜이다).
     * 1박 2일~2박 3일 바구니는 보통 열 곳 안팎이라 15면 대부분을 덮으면서,
     * 여러 목적지 API 의 목적지 상한(30)도 자연히 지킨다(장소 15개 → 목적지 14개).
     * 이보다 많으면 콜 수 대비 이득이 적어 앞쪽 장소까지만 실측하고 나머지 구간은 직선거리로 둔다.
     */
    private static final int MAX_ORIGIN_CALLS = 15;

    private final KakaoMobilityClient kakaoMobilityClient;

    /**
     * @param places 좌표가 있는 장소만 실측 대상이 된다. 두 곳 미만이면 조회할 구간이 없어 빈 행렬을 반환한다.
     */
    public TravelMatrix resolve(Collection<SchedulingPlace> places) {
        List<SchedulingPlace> targets = places.stream()
                .filter(place -> place != null && place.hasCoordinates())
                .limit(MAX_ORIGIN_CALLS)
                .toList();
        if (targets.size() < 2) {
            return TravelMatrix.empty();
        }

        Map<String, TravelMatrix.Leg> legs = new HashMap<>();
        long totalMeters = 0;
        long totalSeconds = 0;

        for (SchedulingPlace origin : targets) {
            List<SchedulingPlace> destinations = targets.stream()
                    .filter(place -> !place.contentId().equals(origin.contentId()))
                    .toList();
            KakaoMultiDestResponse response = fetch(origin, destinations);
            if (response == null) {
                // 한 콜이 실패하면 나머지도 같은 이유로 실패할 가능성이 높다. 남은 콜로 응답만 늦추지 않고
                // 여기까지 모은 부분 행렬로 진행한다(재시도도 하지 않는다).
                break;
            }
            for (KakaoMultiDestResponse.Route route : response.routes()) {
                if (!isSuccess(route)) {
                    continue;
                }
                KakaoMultiDestResponse.Summary summary = route.summary();
                legs.put(TravelMatrix.key(origin.contentId(), route.key()), toLeg(summary));
                totalMeters += summary.distance();
                totalSeconds += summary.duration() == null ? 0 : summary.duration();
            }
        }

        if (legs.isEmpty()) {
            log.warn("[일정] 도로 거리 행렬을 하나도 확보하지 못해 직선거리로 진행합니다. 장소={}건", targets.size());
            return TravelMatrix.empty();
        }
        return new TravelMatrix(legs, observedSpeedKmh(totalMeters, totalSeconds));
    }

    /** 실패·타임아웃은 예외로 올리지 않고 null 로 알린다. */
    private KakaoMultiDestResponse fetch(SchedulingPlace origin, List<SchedulingPlace> destinations) {
        KakaoMultiDestRequest request = new KakaoMultiDestRequest(
                new KakaoMultiDestRequest.Point(origin.longitude(), origin.latitude()),
                destinations.stream()
                        .map(d -> new KakaoMultiDestRequest.Destination(
                                d.longitude(), d.latitude(), d.contentId()))
                        .toList(),
                radiusMetersFor(origin, destinations)
        );

        try {
            KakaoMultiDestResponse response = kakaoMobilityClient.getMultiDestinationDirections(request);
            return (response == null || response.routes() == null) ? null : response;
        } catch (RuntimeException e) {
            log.warn("[일정] Kakao Mobility 길찾기 실패 - 직선거리로 폴백: {}", e.getMessage());
            return null;
        }
    }

    /** 가장 먼 목적지까지 덮도록 반경을 잡되 API 상한(10km)으로 제한한다. 그 밖의 목적지는 응답에서 빠진다. */
    private static int radiusMetersFor(SchedulingPlace origin, List<SchedulingPlace> destinations) {
        double maxKm = destinations.stream()
                .mapToDouble(d -> GeoDistance.kilometers(
                        origin.latitude(), origin.longitude(), d.latitude(), d.longitude()))
                .max()
                .orElse(0.0);
        int radius = (int) Math.ceil(maxKm * 1000.0) + 100;
        return Math.min(Math.max(radius, 100), KAKAO_RADIUS_MAX_METERS);
    }

    /**
     * 실측에 성공한 구간 전체의 평균 속도(도로 거리 합 ÷ 소요 시간 합).
     * 반경 밖이라 빠진 구간을 직선거리로 환산할 때 이 지역에서 실제로 관측된 속도를 쓰기 위함이다.
     * 성공 구간이 없거나 소요 시간이 0이면 정책 기본 속도를 쓴다.
     */
    private static double observedSpeedKmh(long totalMeters, long totalSeconds) {
        if (totalSeconds <= 0) {
            return SchedulingPolicy.AVG_SPEED_KMH;
        }
        return (totalMeters / 1000.0) / (totalSeconds / 3600.0);
    }

    private static boolean isSuccess(KakaoMultiDestResponse.Route route) {
        return route != null
                && route.key() != null
                && route.resultCode() != null && route.resultCode() == 0
                && route.summary() != null && route.summary().distance() != null;
    }

    private static TravelMatrix.Leg toLeg(KakaoMultiDestResponse.Summary summary) {
        double km = summary.distance() / 1000.0;
        // 소요 시간이 없으면 거리만으로 환산한다. 거리가 있는 구간을 0분으로 두면 같은 시각에 두 장소에 있게 된다.
        int minutes = summary.duration() != null
                ? Math.max((int) Math.round(summary.duration() / 60.0), 1)
                : Math.max((int) Math.ceil(km / SchedulingPolicy.AVG_SPEED_KMH * 60), 1);
        return new TravelMatrix.Leg(km, minutes);
    }
}
