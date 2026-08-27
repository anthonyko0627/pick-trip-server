package travel_agency.pick_trip.domain.itinerary.scheduling;

import travel_agency.pick_trip.domain.content.adapter.ContentTypeCategory;

/**
 * 콘텐츠 타입별 체류 시간 조회. 타입별 실제 값은 {@link ContentTypeCategory} 가 단일 원천으로 보유한다.
 */
public final class StayDurationPolicy {

    private StayDurationPolicy() {
    }

    public static int stayMinutes(Integer contentTypeId) {
        if (contentTypeId == null) {
            return SchedulingPolicy.DEFAULT_STAY_MINUTES;
        }
        return ContentTypeCategory.stayMinutesFor(contentTypeId);
    }
}
