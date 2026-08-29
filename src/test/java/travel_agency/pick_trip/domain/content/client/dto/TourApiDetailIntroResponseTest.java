package travel_agency.pick_trip.domain.content.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse.Item;

/**
 * {@code /detailIntro2} 는 콘텐츠 타입마다 운영시간·휴무일·주차 필드명이 다르다.
 * 관광지(12) 외 타입의 값이 버려지지 않는지, 이름만 비슷한 필드를 잘못 집지 않는지 검증한다.
 *
 * <p>필드가 22개라 위치 인자를 테스트마다 나열하면 실수가 흩어진다.
 * 타입별 팩터리로 한 곳에 가둔다.
 */
class TourApiDetailIntroResponseTest {

    private static Item tourism(String useTime, String restDate, String parking) {
        return new Item("1", "12", useTime, restDate, parking,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null);
    }

    private static Item culture(String useTime, String restDate, String parking) {
        return new Item("1", "14", null, null, null,
                useTime, restDate, parking, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null);
    }

    private static Item festival(String playTime) {
        return new Item("1", "15", null, null, null,
                null, null, null, playTime, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null);
    }

    private static Item leports(String useTime, String restDate, String parking) {
        return new Item("1", "28", null, null, null,
                null, null, null, null, useTime, restDate, parking,
                null, null, null, null, null, null, null,
                null, null, null);
    }

    private static Item shopping(String openTime, String restDate, String parking, String babyCarriage) {
        return new Item("1", "38", null, null, null,
                null, null, null, null, null, null, null,
                openTime, restDate, parking, babyCarriage, null, null, null,
                null, null, null);
    }

    private static Item food(String openTime, String restDate, String parking) {
        return new Item("1", "39", null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, openTime, restDate, parking,
                null, null, null);
    }

    @Test
    @DisplayName("관광지(12)는 usetime·restdate·parking 을 그대로 읽는다.")
    void resolveTourismFields() {
        // given
        Item item = tourism("09:00~18:00", "매주 월요일", "가능");

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("09:00~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 월요일");
        assertThat(item.resolvedParking()).isEqualTo("가능");
    }

    @Test
    @DisplayName("문화시설(14)은 usetimeculture·restdateculture·parkingculture 를 읽는다.")
    void resolveCultureFields() {
        // given
        Item item = culture("화요일~일요일 10:00~18:00", "매주 월요일", "가능");

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("화요일~일요일 10:00~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 월요일");
        assertThat(item.resolvedParking()).isEqualTo("가능");
    }

    @Test
    @DisplayName("음식점(39)은 opentimefood·restdatefood·parkingfood 를 읽는다.")
    void resolveFoodFields() {
        // given
        Item item = food("11:30~18:00", "매주 화요일~수요일", "가능");

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("11:30~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 화요일~수요일");
        assertThat(item.resolvedParking()).isEqualTo("가능");
    }

    @Test
    @DisplayName("쇼핑(38)은 opentime·restdateshopping·parkingshopping·chkbabycarriageshopping 을 읽는다.")
    void resolveShoppingFields() {
        // given
        Item item = shopping("10:00~18:00", "매주 일요일", "가능 ( 약 소형 6대 / 대형 4대 )", "없음");

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("10:00~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 일요일");
        assertThat(item.resolvedParking()).isEqualTo("가능 ( 약 소형 6대 / 대형 4대 )");
        assertThat(item.resolvedBabyCarriage()).isEqualTo("없음");
    }

    @Test
    @DisplayName("레포츠(28)는 usetimeleports·restdateleports·parkingleports 를 읽는다.")
    void resolveLeportsFields() {
        // given
        Item item = leports("09:00~17:00", "연중 무휴", "가능");

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("09:00~17:00");
        assertThat(item.resolvedRestDate()).isEqualTo("연중 무휴");
        assertThat(item.resolvedParking()).isEqualTo("가능");
    }

    @Test
    @DisplayName("축제(15)는 playtime 을 운영시간으로 읽는다.")
    void resolveFestivalPlayTime() {
        // given
        Item item = festival("09:00~18:00");

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("09:00~18:00");
    }

    @Test
    @DisplayName("주차 요금 필드는 주차 가능 여부로 읽지 않는다.")
    void ignoreParkingFeeFields() {
        // given: TourAPI 는 parkingfee/parkingfeeleports 에 "무료" 같은 요금을 넣는다.
        // record 에 그 필드가 없으므로 역직렬화돼도 무시된다(@JsonIgnoreProperties).
        Item onlyFeePresent = culture("10:00~18:00", "매주 월요일", null);

        // when & then
        assertThat(onlyFeePresent.resolvedParking()).isNull();
    }

    @Test
    @DisplayName("이름만 비슷한 필드(restroom·opendate·usetimefestival)는 매핑 후보에 없다.")
    void ignoreLookalikeFields() {
        // given
        Item empty = tourism(null, null, null);

        // when & then
        assertThat(empty.resolvedUseTime()).isNull();
        assertThat(empty.resolvedRestDate()).isNull();
        assertThat(empty.resolvedParking()).isNull();
        assertThat(empty.resolvedBabyCarriage()).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 값이 없는 것으로 보고 다음 후보로 넘어간다.")
    void treatBlankAsMissing() {
        // given
        Item item = new Item("1", "39", "  ", "", "   ",
                null, null, null, null, null, null, null,
                null, null, null, null, "11:30~18:00", "매주 월요일", "가능",
                null, null, null);

        // when & then
        assertThat(item.resolvedUseTime()).isEqualTo("11:30~18:00");
        assertThat(item.resolvedRestDate()).isEqualTo("매주 월요일");
        assertThat(item.resolvedParking()).isEqualTo("가능");
    }
}
