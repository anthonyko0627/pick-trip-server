package travel_agency.pick_trip.domain.content.dto.response;

import java.util.List;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;

public record ContentDetailResponse(
        String contentId,
        String title,
        int contentTypeId,
        String address,
        String tel,
        String homepage,
        double latitude,
        double longitude,
        String summary,
        String useTime,
        String restDate,
        String parking,
        String useFee,
        String chkBabyCarriage,
        String chkPet,
        String stayDuration,
        Boolean reservationRequired,
        String dataSource,
        List<ImageItem> images,
        ContentCategory category,
        boolean indoor,
        String region,
        VisitorStatsResponse visitorStats
) {
    public record ImageItem(String imageUrl, String title) {}

    /** 관광객수 지표를 덧붙인 사본. 지표를 만들 수 없으면 {@code null} 을 그대로 담는다. */
    public ContentDetailResponse withVisitorStats(VisitorStatsResponse visitorStats) {
        return new ContentDetailResponse(
                contentId, title, contentTypeId, address, tel, homepage, latitude, longitude, summary,
                useTime, restDate, parking, useFee, chkBabyCarriage, chkPet, stayDuration,
                reservationRequired, dataSource, images, category, indoor, region, visitorStats);
    }
}
