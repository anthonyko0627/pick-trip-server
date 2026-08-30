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
