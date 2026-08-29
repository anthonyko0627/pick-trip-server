package travel_agency.pick_trip.domain.content.dto.response;

import java.util.List;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;

/**
 * 특정 콘텐츠 좌표를 기준으로 반경 내 주변 콘텐츠를 거리순으로 담는다.
 * {@code travel_contents} 로컬 적재분만을 대상으로 하며, 거리는 Haversine 근사값(km)이다.
 */
public record NearbyContentResponse(
        String originContentId,
        double radiusKm,
        List<NearbyContentItem> items
) {

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
            double distanceKm
    ) {
    }
}
