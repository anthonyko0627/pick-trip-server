package travel_agency.pick_trip.domain.content.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapter;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;
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
     * 기준 콘텐츠가 로컬에 없으면 {@code CONTENT_NOT_FOUND}, 좌표가 없거나 (0, 0)이면 {@code CONTENT_LOCATION_UNKNOWN}.
     */
    @Transactional(readOnly = true)
    public NearbyContentResponse getNearbyContents(String contentId, double radiusKm, int size) {
        double effectiveRadiusKm = clampRadiusKm(radiusKm);
        int effectiveSize = clampSize(size);

        TravelContent origin = travelContentRepository.findById(contentId)
                .orElseThrow(() -> new ContentException(ErrorCode.CONTENT_NOT_FOUND));

        Double lat = origin.getLatitude();
        Double lng = origin.getLongitude();
        if (lat == null || lng == null || (lat == 0.0 && lng == 0.0)) {
            throw new ContentException(ErrorCode.CONTENT_LOCATION_UNKNOWN);
        }

        List<NearbyContentItem> items = travelContentRepository
                .findNearby(contentId, lat, lng, effectiveRadiusKm, effectiveSize)
                .stream()
                .map(ContentService::toItem)
                .toList();

        return new NearbyContentResponse(contentId, effectiveRadiusKm, items);
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
