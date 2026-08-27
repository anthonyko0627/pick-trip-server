package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailIntroResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    /**
     * {@code /detailIntro2} 는 콘텐츠 타입마다 운영시간·휴무일의 필드명이 다르다.
     * {@code usetime}/{@code restdate} 만 읽으면 관광지(12) 외 전 타입의 값이 버려지므로
     * 타입별 필드를 모두 받아두고 {@link #resolvedUseTime()}·{@link #resolvedRestDate()} 로 골라 쓴다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            // 12 관광지
            String usetime,
            String restdate,
            // 14 문화시설
            String usetimeculture,
            String restdateculture,
            // 15 축제 (usetimefestival 은 시각이 아니라 요금이 들어오므로 쓰지 않는다)
            String playtime,
            // 28 레포츠
            String usetimeleports,
            String restdateleports,
            // 38 쇼핑
            String opentime,
            String restdateshopping,
            // 39 음식점
            String opentimefood,
            String restdatefood,
            String parking,
            String usefee,
            String chkbabycarriage,
            String chkpet
    ) {

        /**
         * 타입별 운영시간 필드 중 실제로 채워진 값을 고른다.
         * {@code opendate}("1998년 4월 18일 개장")·{@code opendateshopping}("2, 7일(장 서는날)")·
         * {@code usetimefestival}("축제장 입장료 2,000원…")은 이름이 비슷할 뿐 운영시간이 아니라 제외한다.
         */
        public String resolvedUseTime() {
            return firstNonBlank(usetime, usetimeculture, opentimefood, opentime, usetimeleports, playtime);
        }

        /**
         * 타입별 휴무일 필드 중 실제로 채워진 값을 고른다.
         * {@code restroom}("있음")은 화장실 유무라 이름만 비슷하고 휴무일이 아니다.
         */
        public String resolvedRestDate() {
            return firstNonBlank(restdate, restdateculture, restdatefood, restdateshopping, restdateleports);
        }

        private static String firstNonBlank(String... candidates) {
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return null;
        }
    }
}
