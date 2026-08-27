package travel_agency.pick_trip.domain.itinerary.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoDistanceTest {

    @Test
    @DisplayName("같은 좌표 사이의 거리는 0km 이다.")
    void returnZeroForIdenticalCoordinates() {
        // given
        double lat = 35.0673;
        double lon = 127.7514;

        // when
        double km = GeoDistance.kilometers(lat, lon, lat, lon);

        // then
        assertThat(km).isEqualTo(0.0);
    }

    @Test
    @DisplayName("하동군청과 영주시청 사이 직선거리는 약 208.7km 이다.")
    void calculateDistanceBetweenHadongAndYeongju() {
        // given
        double hadongLat = 35.0673;
        double hadongLon = 127.7514;
        double yeongjuLat = 36.8057;
        double yeongjuLon = 128.6240;

        // when
        double km = GeoDistance.kilometers(hadongLat, hadongLon, yeongjuLat, yeongjuLon);

        // then
        // haversine 손계산 기준값 208.65km, 지구 반경 근사로 인한 오차를 2km 허용한다.
        assertThat(km).isCloseTo(208.7, within(2.0));
    }

    @Test
    @DisplayName("출발지와 도착지를 바꿔도 거리는 같다.")
    void returnSameDistanceWhenOriginAndDestinationSwapped() {
        // given
        double hadongLat = 35.0673;
        double hadongLon = 127.7514;
        double yechonLat = 36.6457;
        double yechonLon = 128.4370;

        // when
        double forward = GeoDistance.kilometers(hadongLat, hadongLon, yechonLat, yechonLon);
        double backward = GeoDistance.kilometers(yechonLat, yechonLon, hadongLat, hadongLon);

        // then
        assertThat(forward).isCloseTo(backward, within(1e-9));
    }

    @Test
    @DisplayName("위경도 부호가 반대인 좌표도 대권 거리로 계산한다.")
    void calculateDistanceAcrossOppositeSigns() {
        // given
        // 적도상 경도 -10도 ~ +10도 = 20도. 위도선/자오선 1도는 40030.17km / 360 = 111.1949km 이다.
        double expectedKm = 20 * (2 * Math.PI * 6371.0 / 360);

        // when
        double alongEquator = GeoDistance.kilometers(0.0, -10.0, 0.0, 10.0);
        double alongMeridian = GeoDistance.kilometers(-10.0, 0.0, 10.0, 0.0);

        // then
        assertThat(alongEquator).isCloseTo(expectedKm, within(0.001));
        assertThat(alongMeridian).isCloseTo(expectedKm, within(0.001));
    }
}
