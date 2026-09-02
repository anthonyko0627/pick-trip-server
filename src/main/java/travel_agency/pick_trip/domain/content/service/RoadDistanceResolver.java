package travel_agency.pick_trip.domain.content.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.KakaoMobilityClient;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestRequest;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.DistanceBasis;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;

/**
 * 직선 거리로 정렬된 근처 콘텐츠 후보를 Kakao Mobility 길찾기로 조회한 실제 도로 거리로 다시 매긴다.
 *
 * <p>후보는 이미 직선 거리 오름차순으로 정렬돼 있다고 가정하며, 그중 앞에서 최대
 * {@code size}개만 1콜로 라우팅한다(사용량 절약). 길찾기가 실패하면 예외를 던지지 않고
 * 입력 순서(직선 거리)를 유지한 채 {@link DistanceBasis#STRAIGHT}로 표시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoadDistanceResolver {

    /** Kakao Mobility 여러 목적지 길찾기 {@code radius} 상한(m). */
    private static final int KAKAO_RADIUS_MAX_METERS = 10_000;

    private final KakaoMobilityClient kakaoMobilityClient;

    /**
     * @param originLat  기준 위도
     * @param originLng  기준 경도
     * @param candidates 직선 거리 오름차순으로 정렬된 후보 (각 항목의 {@code distanceKm}는 직선 거리)
     * @param size       최종 반환·라우팅 개수
     */
    public List<NearbyContentItem> resolve(
            double originLat, double originLng, List<NearbyContentItem> candidates, int size) {
        List<NearbyContentItem> targets = candidates.stream()
                .filter(c -> !(c.latitude() == 0.0 && c.longitude() == 0.0))
                .limit(size)
                .toList();
        if (targets.isEmpty()) {
            return List.of();
        }

        Map<String, KakaoMultiDestResponse.Summary> roadByContentId =
                fetchRoadDistances(originLat, originLng, targets);

        return targets.stream()
                .map(item -> applyRoad(item, roadByContentId.get(item.contentId())))
                .sorted(Comparator.comparingDouble(NearbyContentItem::distanceKm))
                .toList();
    }

    private Map<String, KakaoMultiDestResponse.Summary> fetchRoadDistances(
            double originLat, double originLng, List<NearbyContentItem> targets) {
        KakaoMultiDestRequest request = new KakaoMultiDestRequest(
                new KakaoMultiDestRequest.Point(originLng, originLat),
                targets.stream()
                        .map(t -> new KakaoMultiDestRequest.Destination(t.longitude(), t.latitude(), t.contentId()))
                        .toList(),
                radiusMetersFor(targets)
        );

        try {
            KakaoMultiDestResponse response = kakaoMobilityClient.getMultiDestinationDirections(request);
            if (response == null || response.routes() == null) {
                return Map.of();
            }
            Map<String, KakaoMultiDestResponse.Summary> result = new HashMap<>();
            for (KakaoMultiDestResponse.Route route : response.routes()) {
                if (isSuccess(route)) {
                    result.put(route.key(), route.summary());
                }
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("[근처] Kakao Mobility 길찾기 실패 - 직선 거리로 폴백: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 라우팅 후보 중 가장 먼 직선 거리를 덮도록 반경을 잡되 API 상한(10km)으로 제한한다. */
    private static int radiusMetersFor(List<NearbyContentItem> targets) {
        double maxStraightKm = targets.stream()
                .mapToDouble(NearbyContentItem::distanceKm)
                .max()
                .orElse(0.0);
        int radius = (int) Math.ceil(maxStraightKm * 1000.0) + 100;
        return Math.min(Math.max(radius, 100), KAKAO_RADIUS_MAX_METERS);
    }

    private static boolean isSuccess(KakaoMultiDestResponse.Route route) {
        return route.key() != null
                && route.resultCode() != null && route.resultCode() == 0
                && route.summary() != null && route.summary().distance() != null;
    }

    private static NearbyContentItem applyRoad(NearbyContentItem item, KakaoMultiDestResponse.Summary road) {
        if (road == null) {
            return item.withDistance(item.distanceKm(), null, DistanceBasis.STRAIGHT);
        }
        double roadKm = Math.round(road.distance() / 1000.0 * 100.0) / 100.0;
        Integer minutes = road.duration() != null ? (int) Math.round(road.duration() / 60.0) : null;
        return item.withDistance(roadKm, minutes, DistanceBasis.ROAD);
    }
}
