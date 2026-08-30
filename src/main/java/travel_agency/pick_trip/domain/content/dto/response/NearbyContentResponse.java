package travel_agency.pick_trip.domain.content.dto.response;

import java.util.List;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;

/**
 * 특정 콘텐츠 좌표를 기준으로 반경 내 주변 콘텐츠를 거리순으로 담는다.
 * 거리는 km 근사값이며, {@link NearbySource}로 어느 소스에서 조회했는지 알린다.
 */
public record NearbyContentResponse(
        String originContentId,
        double radiusKm,
        NearbySource source,
        List<NearbyContentItem> items
) {

    /**
     * 주변 콘텐츠 조회 소스.
     * <ul>
     *   <li>{@code LOCAL} — {@code travel_contents} 로컬 적재분에서 Haversine 근사로 조회</li>
     *   <li>{@code TOURAPI} — 로컬에 기준 콘텐츠·좌표·주변 행이 없어 TourAPI {@code locationBasedList2}로 조회</li>
     * </ul>
     */
    public enum NearbySource {
        LOCAL,
        TOURAPI
    }

    /**
     * {@code distanceKm} 산출 기준.
     * <ul>
     *   <li>{@code ROAD} — Kakao Mobility 길찾기로 계산한 실제 자동차 도로 거리</li>
     *   <li>{@code STRAIGHT} — 직선(Haversine) 거리. 길찾기 실패 시 폴백</li>
     * </ul>
     */
    public enum DistanceBasis {
        ROAD,
        STRAIGHT
    }

    public record NearbyContentItem(
            String contentId,
            String title,
            String contentTypeId,
            String address,
            String firstImage,
            double latitude,
            double longitude,
            ContentCategory category,
            String summary,
            String region,
            double distanceKm,
            /** 자동차 소요 시간(분). {@code distanceBasis == STRAIGHT} 이면 {@code null}. */
            Integer durationMinutes,
            DistanceBasis distanceBasis
    ) {

        /** 도로 거리 계산 전 초기 상태(직선 거리)로 생성한다. */
        public NearbyContentItem(
                String contentId, String title, String contentTypeId, String address, String firstImage,
                double latitude, double longitude, ContentCategory category, String summary, String region,
                double distanceKm
        ) {
            this(contentId, title, contentTypeId, address, firstImage, latitude, longitude,
                    category, summary, region, distanceKm, null, DistanceBasis.STRAIGHT);
        }

        /** 거리 관련 필드만 교체한 새 인스턴스를 만든다. */
        public NearbyContentItem withDistance(double distanceKm, Integer durationMinutes, DistanceBasis distanceBasis) {
            return new NearbyContentItem(contentId, title, contentTypeId, address, firstImage,
                    latitude, longitude, category, summary, region, distanceKm, durationMinutes, distanceBasis);
        }
    }
}
