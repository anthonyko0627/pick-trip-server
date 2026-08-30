package travel_agency.pick_trip.domain.content.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapter;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbySource;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.repository.projection.NearbyContentProjection;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

@Service
@RequiredArgsConstructor
public class ContentService {

    static final double DEFAULT_RADIUS_KM = 5.0;
    static final double MAX_RADIUS_KM = 20.0;
    static final int DEFAULT_NEARBY_SIZE = 10;
    static final int MAX_NEARBY_SIZE = 30;

    private final TourApiContentAdapter adapter;
    private final TravelContentRepository travelContentRepository;

    public ContentListResponse getContents(ContentListRequest request) {
        Region region = Region.fromCode(request.region());
        return adapter.fetchList(request, region);
    }

    public ContentDetailResponse getContentDetail(String contentId) {
        return adapter.fetchDetail(contentId);
    }

    /**
     * 기준 콘텐츠의 좌표를 중심으로 반경 내 주변 콘텐츠를 거리순으로 조회한다.
     * {@code radiusKm} 는 (0, {@value #MAX_RADIUS_KM}] 로, {@code size} 는 [1, {@value #MAX_NEARBY_SIZE}] 로 클램프한다.
     *
     * <p>우선 로컬 적재분({@code travel_contents})에서 조회하고, 로컬로 답할 수 없으면
     * ({@code 기준 콘텐츠 미적재 · 좌표 없음/(0,0) · 주변 로컬 행 0건}) TourAPI {@code locationBasedList2}로 폴백한다.
     * 소스는 섞지 않으며 응답의 {@link NearbySource}로 구분한다. 좌표를 로컬·TourAPI 어디에서도 얻지 못하면
     * {@code CONTENT_LOCATION_UNKNOWN}, TourAPI 가 기준 콘텐츠를 모르면 {@code CONTENT_NOT_FOUND}.
     */
    public NearbyContentResponse getNearbyContents(String contentId, double radiusKm, int size) {
        double effectiveRadiusKm = clampRadiusKm(radiusKm);
        int effectiveSize = clampSize(size);

        Optional<TravelContent> localOrigin = travelContentRepository.findById(contentId);
        Double localLat = localOrigin.map(TravelContent::getLatitude).orElse(null);
        Double localLng = localOrigin.map(TravelContent::getLongitude).orElse(null);

        if (hasUsableCoordinates(localLat, localLng)) {
            List<NearbyContentItem> localItems = travelContentRepository
                    .findNearby(contentId, localLat, localLng, effectiveRadiusKm, effectiveSize)
                    .stream()
                    .map(ContentService::toItem)
                    .toList();
            if (!localItems.isEmpty()) {
                return new NearbyContentResponse(
                        contentId, effectiveRadiusKm, NearbySource.LOCAL, localItems);
            }
            return fallbackToProvider(contentId, localLat, localLng, effectiveRadiusKm, effectiveSize);
        }

        // 로컬에 없거나 좌표가 불완전 → TourAPI 상세로 좌표를 확보한다.
        // fetchDetail 이 CONTENT_NOT_FOUND / CONTENT_PROVIDER_FAILED 를 그대로 위임한다.
        ContentDetailResponse detail = adapter.fetchDetail(contentId);
        if (!hasUsableCoordinates(detail.latitude(), detail.longitude())) {
            throw new ContentException(ErrorCode.CONTENT_LOCATION_UNKNOWN);
        }
        return fallbackToProvider(
                contentId, detail.latitude(), detail.longitude(), effectiveRadiusKm, effectiveSize);
    }

    private NearbyContentResponse fallbackToProvider(
            String originContentId, double lat, double lng, double radiusKm, int size) {
        List<NearbyContentItem> items = adapter.fetchNearby(originContentId, lat, lng, radiusKm, size);
        return new NearbyContentResponse(originContentId, radiusKm, NearbySource.TOURAPI, items);
    }

    private static boolean hasUsableCoordinates(Double lat, Double lng) {
        return lat != null && lng != null && !(lat == 0.0 && lng == 0.0);
    }

    private static double clampRadiusKm(double radiusKm) {
        if (Double.isNaN(radiusKm) || radiusKm <= 0) {
            return DEFAULT_RADIUS_KM;
        }
        return Math.min(radiusKm, MAX_RADIUS_KM);
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_NEARBY_SIZE;
        }
        return Math.min(size, MAX_NEARBY_SIZE);
    }

    private static NearbyContentItem toItem(NearbyContentProjection row) {
        return new NearbyContentItem(
                row.getSourceContentId(),
                row.getTitle(),
                row.getContentTypeId(),
                row.getAddress(),
                row.getFirstImage(),
                row.getLatitude() != null ? row.getLatitude() : 0.0,
                row.getLongitude() != null ? row.getLongitude() : 0.0,
                row.getCategory() != null ? ContentCategory.valueOf(row.getCategory()) : null,
                row.getSummary(),
                row.getRegion(),
                roundToTwo(row.getDistanceKm())
        );
    }

    private static double roundToTwo(Double distanceKm) {
        if (distanceKm == null) {
            return 0.0;
        }
        return Math.round(distanceKm * 100.0) / 100.0;
    }
}
