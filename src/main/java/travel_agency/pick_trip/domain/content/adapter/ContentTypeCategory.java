package travel_agency.pick_trip.domain.content.adapter;

import travel_agency.pick_trip.domain.content.dto.request.CompanionType;

public enum ContentTypeCategory {
    TOURISM("12", "약 2시간", 120),
    CULTURE("14", "약 1~2시간", 90),
    EVENT("15", "약 2~3시간", 150),
    LEISURE("28", "약 2~3시간", 150),
    SHOPPING("38", "약 1시간", 60),
    RESTAURANT("39", "약 1시간", 60);

    /** 타입을 특정할 수 없는 콘텐츠의 기본 체류 시간. */
    private static final int DEFAULT_STAY_MINUTES = 90;

    private final String contentTypeId;
    private final String stayDuration;
    /** stayDuration 의 사람이 읽는 범위 표현을 스케줄링이 계산에 쓸 수 있도록 단일 대표값으로 고정한 것. */
    private final int stayMinutes;

    ContentTypeCategory(String contentTypeId, String stayDuration, int stayMinutes) {
        this.contentTypeId = contentTypeId;
        this.stayDuration = stayDuration;
        this.stayMinutes = stayMinutes;
    }

    public static String resolveContentTypeId(String explicit, Boolean indoorOnly, CompanionType companion) {
        if (explicit != null) return explicit;
        if (indoorOnly != null) {
            return indoorOnly ? CULTURE.contentTypeId : TOURISM.contentTypeId;
        }
        if (companion == CompanionType.FAMILY) {
            return CULTURE.contentTypeId;
        }
        return null;
    }

    public static String stayDurationFor(int contentTypeId) {
        String id = String.valueOf(contentTypeId);
        for (ContentTypeCategory c : values()) {
            if (c.contentTypeId.equals(id)) {
                return c.stayDuration;
            }
        }
        return null;
    }

    public static int stayMinutesFor(int contentTypeId) {
        String id = String.valueOf(contentTypeId);
        for (ContentTypeCategory c : values()) {
            if (c.contentTypeId.equals(id)) {
                return c.stayMinutes;
            }
        }
        return DEFAULT_STAY_MINUTES;
    }
}
