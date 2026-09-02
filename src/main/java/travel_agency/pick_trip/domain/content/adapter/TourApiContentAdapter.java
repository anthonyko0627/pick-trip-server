package travel_agency.pick_trip.domain.content.adapter;

import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiLocationListResponse;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

@Component
@RequiredArgsConstructor
public class TourApiContentAdapter {

    /** locationBasedList2 {@code radius} 상한(m). TourAPI 스펙상 20km 를 넘는 값은 거부된다. */
    private static final int LOCATION_RADIUS_MAX_METERS = 20_000;
    /** TourAPI {@code arrange} 코드 — E: 거리순. */
    private static final String ARRANGE_BY_DISTANCE = "E";

    private final TourApiClient tourApiClient;
    private final TourApiContentMapper mapper;

    public ContentListResponse fetchList(ContentListRequest request, Region region) {
        int pageNo = request.page() + 1;
        String effectiveContentTypeId = ContentTypeCategory.resolveContentTypeId(
                request.contentTypeId(), request.indoorOnly(), request.companion()
        );
        try {
            TourApiListResponse raw;
            if (request.keyword() != null && !request.keyword().isBlank()) {
                raw = tourApiClient.searchByKeyword(
                        request.keyword(),
                        region.getLDongRegnCd(),
                        region.getLDongSignguCd(),
                        effectiveContentTypeId,
                        pageNo,
                        request.size()
                );
            } else {
                raw = tourApiClient.getAreaBasedList(
                        region.getLDongRegnCd(),
                        region.getLDongSignguCd(),
                        effectiveContentTypeId,
                        pageNo,
                        request.size()
                );
            }
            if (raw.isError()) {
                throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
            }
            return mapper.toListResponse(raw, request.page(), request.size(), region);
        } catch (FeignException e) {
            throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }

    /**
     * 상세 조회. 한 건에 {@code detailCommon2}·{@code detailIntro2}·{@code detailImage2} 로 3콜이 나가므로
     * TourAPI 일일 요청 한도를 아끼기 위해 {@code contentId} 기준으로 캐시한다.
     * 예외는 캐시되지 않으므로 일시적 실패(429)나 {@code CONTENT_NOT_FOUND} 가 굳지 않는다.
     */
    /**
     * 좌표·반경 기반 주변 콘텐츠 조회. 로컬 적재분으로 답할 수 없을 때({@code travel_contents}에 기준 콘텐츠·좌표·주변 행이 없음)
     * {@link travel_agency.pick_trip.domain.content.service.ContentService}가 폴백으로 호출한다.
     * 기준 콘텐츠 제외·비MVP 타입 제외 후 거리순 상위 {@code size}개를 반환하도록 여유분을 요청한다.
     */
    public List<NearbyContentItem> fetchNearby(
            String originContentId, double latitude, double longitude, double radiusKm, int size) {
        int radiusMeters = (int) Math.min(Math.round(radiusKm * 1000.0), LOCATION_RADIUS_MAX_METERS);
        try {
            TourApiLocationListResponse raw = tourApiClient.getLocationBasedList(
                    String.valueOf(longitude),
                    String.valueOf(latitude),
                    radiusMeters,
                    ARRANGE_BY_DISTANCE,
                    1,
                    size * 2
            );
            if (raw.isError()) {
                throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
            }
            return mapper.toNearbyItems(raw, originContentId, size);
        } catch (FeignException e) {
            throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }

    @Cacheable("contentDetail")
    public ContentDetailResponse fetchDetail(String contentId) {
        try {
            TourApiDetailCommonResponse common = tourApiClient.getDetailCommon(contentId);

            if (common.isError()) {
                throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
            }

            boolean isEmpty = common.response() == null
                    || common.response().body() == null
                    || common.response().body().items() == null
                    || common.response().body().items().item() == null
                    || common.response().body().items().item().isEmpty();

            if (isEmpty) {
                throw new ContentException(ErrorCode.CONTENT_NOT_FOUND);
            }

            String contentTypeId = common.response().body().items().item().get(0).contenttypeid();
            TourApiDetailIntroResponse intro = tourApiClient.getDetailIntro(contentId, contentTypeId);
            TourApiDetailImageResponse image = tourApiClient.getDetailImage(contentId);

            return mapper.toDetailResponse(common, intro, image);
        } catch (ContentException e) {
            throw e;
        } catch (FeignException e) {
            throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }
}
