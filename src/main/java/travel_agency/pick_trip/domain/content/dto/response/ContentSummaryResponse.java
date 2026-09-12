package travel_agency.pick_trip.domain.content.dto.response;

import travel_agency.pick_trip.domain.content.entity.ContentCategory;

public record ContentSummaryResponse(
        String contentId,
        String title,
        int contentTypeId,
        String address,
        String firstImage,
        double latitude,
        double longitude,
        ContentCategory category,
        String summary,
        boolean indoor,
        String region,
        VisitorStatsResponse visitorStats
) {

    /** 관광객수 지표를 덧붙인 사본. 지표를 만들 수 없으면 {@code null} 을 그대로 담는다. */
    public ContentSummaryResponse withVisitorStats(VisitorStatsResponse visitorStats) {
        return new ContentSummaryResponse(
                contentId, title, contentTypeId, address, firstImage, latitude, longitude,
                category, summary, indoor, region, visitorStats);
    }
}
