package travel_agency.pick_trip.domain.region;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Region")
class RegionTest {

    @Test
    @DisplayName("법정동 코드로 지역을 역매핑한다")
    void fromLdongCode_resolvesRegion() {
        // given & when & then
        assertThat(Region.fromLdongCode("47", "210")).isEqualTo(Region.YEONGJU);
        assertThat(Region.fromLdongCode("47", "900")).isEqualTo(Region.YECHEON);
        assertThat(Region.fromLdongCode("48", "850")).isEqualTo(Region.HADONG);
    }

    @Test
    @DisplayName("법정동 코드가 비어 있거나 대상 지역 밖이면 null을 반환한다")
    void fromLdongCode_returnsNullWhenOutOfScope() {
        // given & when & then
        assertThat(Region.fromLdongCode("", "")).isNull();
        assertThat(Region.fromLdongCode(null, null)).isNull();
        assertThat(Region.fromLdongCode("11", "110")).isNull();
    }

    @Test
    @DisplayName("legacy areaCode가 아닌 법정동 코드를 노출한다")
    void exposesLdongCodes() {
        // given & when & then
        assertThat(Region.YEONGJU.getLDongRegnCd()).isEqualTo("47");
        assertThat(Region.YEONGJU.getLDongSignguCd()).isEqualTo("210");
    }
}
