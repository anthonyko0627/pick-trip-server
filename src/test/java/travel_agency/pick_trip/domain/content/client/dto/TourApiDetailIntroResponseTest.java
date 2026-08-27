package travel_agency.pick_trip.domain.content.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /detailIntro2} 는 콘텐츠 타입마다 운영시간·휴무일 필드명이 다르다.
 * 관광지(12) 외 타입의 값이 버려지지 않는지, 이름만 비슷한 필드를 잘못 집지 않는지 검증한다.
 */
class TourApiDetailIntroResponseTest {

    /** 검증 대상 두 필드 외에는 관심이 없어 나머지는 null 로 채우는 빌더. */
    private TourApiDetailIntroResponse.Item item(String usetime, String restdate,
                                                 String usetimeculture, String restdateculture,
                                                 String playtime,
                                                 String usetimeleports, String restdateleports,
                                                 String opentime, String restdateshopping,
                                                 String opentimefood, String restdatefood) {
        return new TourApiDetailIntroResponse.Item(
                "1", "12", usetime, restdate, usetimeculture, restdateculture, playtime,
                usetimeleports, restdateleports, opentime, restdateshopping,
                opentimefood, restdatefood, null, null, null, null);
    }

    @Test
    @DisplayName("관광지(12)는 usetime·restdate 를 그대로 읽는다.")
    void resolveTourismFields() {
        // given
        TourApiDetailIntroResponse.Item item =
                item("09:00~18:00", "매주 월요일", null, null, null, null, null, null, null, null, null);

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("09:00~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 월요일");
    }

    @Test
    @DisplayName("문화시설(14)은 usetimeculture·restdateculture 를 읽는다.")
    void resolveCultureFields() {
        // given
        TourApiDetailIntroResponse.Item item =
                item(null, null, "화요일~일요일 10:00~18:00", "매주 월요일", null, null, null, null, null, null, null);

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("화요일~일요일 10:00~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 월요일");
    }

    @Test
    @DisplayName("음식점(39)은 opentimefood·restdatefood 를 읽는다.")
    void resolveFoodFields() {
        // given
        TourApiDetailIntroResponse.Item item =
                item(null, null, null, null, null, null, null, null, null,
                        "11:30~18:00", "매주 화요일~수요일");

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("11:30~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 화요일~수요일");
    }

    @Test
    @DisplayName("쇼핑(38)은 opentime·restdateshopping 을 읽는다.")
    void resolveShoppingFields() {
        // given
        TourApiDetailIntroResponse.Item item =
                item(null, null, null, null, null, null, null, "10:00~18:00", "매주 일요일", null, null);

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("10:00~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 일요일");
    }

    @Test
    @DisplayName("레포츠(28)는 usetimeleports·restdateleports 를 읽는다.")
    void resolveLeportsFields() {
        // given
        TourApiDetailIntroResponse.Item item =
                item(null, null, null, null, null, "09:00~17:00", "연중 무휴", null, null, null, null);

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("09:00~17:00");
        assertThat(item.resolvedRestDate()).isEqualTo("연중 무휴");
    }

    @Test
    @DisplayName("축제(15)는 playtime 을 운영시간으로 읽는다.")
    void resolveFestivalPlayTime() {
        // given
        TourApiDetailIntroResponse.Item item =
                item(null, null, null, null, "09:00~18:00", null, null, null, null, null, null);

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("09:00~18:00");
    }

    @Test
    @DisplayName("이름만 비슷한 필드(restroom·opendate·usetimefestival)는 매핑 후보에 없다.")
    void ignoreLookalikeFields() {
        // given: TourAPI 는 usetimefestival 에 요금을, restroom 에 화장실 유무를 넣는다.
        // 두 필드는 record 에 존재하지 않으므로 역직렬화돼도 무시된다(@JsonIgnoreProperties).
        TourApiDetailIntroResponse.Item empty =
                item(null, null, null, null, null, null, null, null, null, null, null);

        // when & then
        assertThat(empty.resolvedUseTime()).isNull();
        assertThat(empty.resolvedRestDate()).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 값이 없는 것으로 보고 다음 후보로 넘어간다.")
    void treatBlankAsMissing() {
        // given
        TourApiDetailIntroResponse.Item item =
                item("  ", "", null, null, null, null, null, null, null, "11:30~18:00", "매주 월요일");

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("11:30~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 월요일");
    }
}
