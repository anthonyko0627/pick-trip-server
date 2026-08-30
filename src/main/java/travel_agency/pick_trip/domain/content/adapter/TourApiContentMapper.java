package travel_agency.pick_trip.domain.content.adapter;

import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiLocationListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentSummaryResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class TourApiContentMapper {

    /**
     * 주변 조회 폴백에서 노출할 콘텐츠 타입(관광지·문화시설·축제·여행코스·레포츠·쇼핑·음식).
     * 로컬 적재 대상({@code ContentCollectService})과 동일하게 맞춰 앱이 다루지 못하는 타입(숙박 등)을 제외한다.
     */
    private static final List<String> NEARBY_CONTENT_TYPE_IDS =
            List.of("12", "14", "15", "25", "28", "38", "39");

    public ContentListResponse toListResponse(TourApiListResponse raw, int page, int size, Region region) {
        List<ContentSummaryResponse> items = Optional.ofNullable(raw.response())
                .map(TourApiListResponse.Response::body)
                .map(TourApiListResponse.Body::items)
                .map(TourApiListResponse.Items::item)
                .orElse(Collections.emptyList())
                .stream()
                .map(item -> toSummaryResponse(item, region))
                .toList();

        int totalCount = Optional.ofNullable(raw.response())
                .map(TourApiListResponse.Response::body)
                .map(TourApiListResponse.Body::totalCount)
                .orElse(0);

        return new ContentListResponse(totalCount, page, size, items);
    }

    public ContentDetailResponse toDetailResponse(
            TourApiDetailCommonResponse common,
            TourApiDetailIntroResponse intro,
            TourApiDetailImageResponse image
    ) {
        TourApiDetailCommonResponse.Item commonItem = extractFirst(common);
        if (commonItem == null) {
            throw new ContentException(ErrorCode.CONTENT_NOT_FOUND);
        }
        TourApiDetailIntroResponse.Item introItem = extractFirst(intro);
        List<ContentDetailResponse.ImageItem> images = extractImages(image);
        int contentTypeId = parseIntOrZero(commonItem.contenttypeid());
        ContentCategory category = ContentCategory.resolve(
                commonItem.lclsSystm1(), commonItem.lclsSystm2(), commonItem.contenttypeid());
        Region region = Region.fromLdongCode(commonItem.lDongRegnCd(), commonItem.lDongSignguCd());

        return new ContentDetailResponse(
                commonItem.contentid(),
                commonItem.title(),
                contentTypeId,
                buildAddress(commonItem.addr1(), commonItem.addr2()),
                commonItem.tel(),
                commonItem.homepage(),
                // TourApiDetailCommonResponse.Item 필드 순서: mapx, mapy (위도=mapy, 경도=mapx)
                parseDouble(commonItem.mapy()),
                parseDouble(commonItem.mapx()),
                commonItem.overview(),
                introItem != null ? introItem.resolvedUseTime() : null,
                introItem != null ? introItem.resolvedRestDate() : null,
                introItem != null ? introItem.resolvedParking() : null,
                introItem != null ? introItem.usefee() : null,
                introItem != null ? introItem.resolvedBabyCarriage() : null,
                introItem != null ? introItem.chkpet() : null,
                ContentTypeCategory.stayDurationFor(contentTypeId),
                null,
                "TourAPI",
                images,
                category,
                category.isIndoor(),
                region != null ? region.name() : null
        );
    }

    /**
     * {@code locationBasedList2} 응답을 주변 콘텐츠 아이템으로 변환한다. 기준 콘텐츠 자신, MVP 외 타입,
     * 좌표가 없는 항목을 제외하고 거리 오름차순으로 상위 {@code size}개만 남긴다.
     * {@code summary}는 이 API 가 제공하지 않아 항상 {@code null}이다.
     */
    public List<NearbyContentItem> toNearbyItems(
            TourApiLocationListResponse raw, String originContentId, int size) {
        return Optional.ofNullable(raw.response())
                .map(TourApiLocationListResponse.Response::body)
                .map(TourApiLocationListResponse.Body::items)
                .map(TourApiLocationListResponse.Items::item)
                .orElse(Collections.emptyList())
                .stream()
                .filter(item -> !originContentId.equals(item.contentid()))
                .filter(item -> NEARBY_CONTENT_TYPE_IDS.contains(item.contenttypeid()))
                .filter(item -> parseDouble(item.mapy()) != 0.0 && parseDouble(item.mapx()) != 0.0)
                .sorted(Comparator.comparingDouble(item -> parseDistanceKm(item.dist())))
                .limit(size)
                .map(this::toNearbyItem)
                .toList();
    }

    private NearbyContentItem toNearbyItem(TourApiLocationListResponse.Item item) {
        ContentCategory category = ContentCategory.resolve(
                item.lclsSystm1(), item.lclsSystm2(), item.contenttypeid());
        Region region = Region.fromLdongCode(item.lDongRegnCd(), item.lDongSignguCd());
        return new NearbyContentItem(
                item.contentid(),
                item.title(),
                item.contenttypeid(),
                buildAddress(item.addr1(), item.addr2()),
                item.firstimage(),
                parseDouble(item.mapy()),
                parseDouble(item.mapx()),
                category,
                null,
                region != null ? region.name() : null,
                roundToTwo(parseDistanceKm(item.dist()))
        );
    }

    private static double parseDistanceKm(String distMeters) {
        if (distMeters == null || distMeters.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(distMeters) / 1000.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double roundToTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private ContentSummaryResponse toSummaryResponse(TourApiListResponse.Item item, Region region) {
        ContentCategory category = ContentCategory.resolve(
                item.lclsSystm1(), item.lclsSystm2(), item.contenttypeid());
        return new ContentSummaryResponse(
                item.contentid(),
                item.title(),
                parseIntOrZero(item.contenttypeid()),
                buildAddress(item.addr1(), item.addr2()),
                item.firstimage(),
                parseDouble(item.mapy()),
                parseDouble(item.mapx()),
                category,
                null,
                category.isIndoor(),
                region.name()
        );
    }

    private TourApiDetailCommonResponse.Item extractFirst(TourApiDetailCommonResponse response) {
        return Optional.ofNullable(response.response())
                .map(TourApiDetailCommonResponse.Response::body)
                .map(TourApiDetailCommonResponse.Body::items)
                .map(TourApiDetailCommonResponse.Items::item)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);
    }

    private TourApiDetailIntroResponse.Item extractFirst(TourApiDetailIntroResponse response) {
        return Optional.ofNullable(response.response())
                .map(TourApiDetailIntroResponse.Response::body)
                .map(TourApiDetailIntroResponse.Body::items)
                .map(TourApiDetailIntroResponse.Items::item)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);
    }

    private List<ContentDetailResponse.ImageItem> extractImages(TourApiDetailImageResponse response) {
        return Optional.ofNullable(response.response())
                .map(TourApiDetailImageResponse.Response::body)
                .map(TourApiDetailImageResponse.Body::items)
                .map(TourApiDetailImageResponse.Items::item)
                .orElse(Collections.emptyList())
                .stream()
                .map(item -> new ContentDetailResponse.ImageItem(item.originimgurl(), item.imgname()))
                .toList();
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0.0; }
    }

    private int parseIntOrZero(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
    }

    private String buildAddress(String addr1, String addr2) {
        if (addr2 == null || addr2.isBlank()) return addr1;
        return addr1 + " " + addr2;
    }
}
