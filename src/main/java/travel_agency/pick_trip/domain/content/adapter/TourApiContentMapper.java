package travel_agency.pick_trip.domain.content.adapter;

import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentSummaryResponse;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class TourApiContentMapper {

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
        Region region = Region.fromAreaCode(commonItem.areacode(), commonItem.sigungucode());

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
