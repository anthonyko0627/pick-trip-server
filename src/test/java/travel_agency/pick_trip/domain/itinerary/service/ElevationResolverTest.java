package travel_agency.pick_trip.domain.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.itinerary.client.ElevationClient;
import travel_agency.pick_trip.domain.itinerary.client.dto.ElevationResponse;
import travel_agency.pick_trip.domain.itinerary.scheduling.ElevationProfile;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPlace;

@ExtendWith(MockitoExtension.class)
@DisplayName("ElevationResolver")
class ElevationResolverTest {

    @Mock private ElevationClient elevationClient;
    @Captor private ArgumentCaptor<String> locationsCaptor;
    @InjectMocks private ElevationResolver resolver;

    private SchedulingPlace place(String id, Double latitude, Double longitude) {
        return new SchedulingPlace(id, "장소 " + id, 12, latitude, longitude, null, 60, false);
    }

    private ElevationResponse response(Double... elevations) {
        return new ElevationResponse(
                "OK",
                Arrays.stream(elevations).map(ElevationResponse.Result::new).toList());
    }

    @Test
    @DisplayName("응답을 요청 순서대로 좌표별 고도에 매핑한다.")
    void mapElevationsByCoordinateInRequestOrder() {
        // given
        List<SchedulingPlace> places = List.of(
                place("A", 35.0672, 127.7517),
                place("B", 36.8056, 128.6241));
        given(elevationClient.getElevations(anyString())).willReturn(response(7.0, 153.0));

        // when
        ElevationProfile profile = resolver.resolve(places);

        // then
        assertThat(profile.metersAt(35.0672, 127.7517)).isEqualTo(7.0);
        assertThat(profile.metersAt(36.8056, 128.6241)).isEqualTo(153.0);
    }

    @Test
    @DisplayName("좌표를 '위도,경도'를 '|'로 이어 한 번에 조회하고 같은 좌표는 중복 요청하지 않는다.")
    void sendsAllCoordinatesInSingleCallWithoutDuplicates() {
        // given
        List<SchedulingPlace> places = List.of(
                place("A", 35.0672, 127.7517),
                place("B", 35.0672, 127.7517),
                place("C", 36.6458, 128.4527));
        given(elevationClient.getElevations(anyString())).willReturn(response(7.0, 87.0));

        // when
        resolver.resolve(places);

        // then
        verify(elevationClient).getElevations(locationsCaptor.capture());
        assertThat(locationsCaptor.getValue()).isEqualTo("35.067200,127.751700|36.645800,128.452700");
    }

    @Test
    @DisplayName("호출이 실패하면 예외 없이 모든 좌표를 고도 미상으로 둔다.")
    void fallsBackToUnknownWhenCallFails() {
        // given
        List<SchedulingPlace> places = List.of(place("A", 35.0672, 127.7517));
        given(elevationClient.getElevations(anyString()))
                .willThrow(new RuntimeException("503 Service Unavailable"));

        // when
        ElevationProfile profile = resolver.resolve(places);

        // then
        assertThat(profile.metersAt(35.0672, 127.7517)).isNull();
    }

    @Test
    @DisplayName("응답 본문이 비어 있어도 예외 없이 고도 미상으로 둔다.")
    void fallsBackToUnknownWhenResponseIsEmpty() {
        // given
        List<SchedulingPlace> places = List.of(place("A", 35.0672, 127.7517));
        given(elevationClient.getElevations(anyString())).willReturn(new ElevationResponse("OK", null));

        // when
        ElevationProfile profile = resolver.resolve(places);

        // then
        assertThat(profile.metersAt(35.0672, 127.7517)).isNull();
    }

    @Test
    @DisplayName("응답에 좌표 일부가 빠지거나 고도가 null 이면 그 좌표만 고도 미상으로 둔다.")
    void keepsPartialResults() {
        // given - 3개를 요청했지만 2개만 오고, 그중 하나는 데이터셋 범위 밖이라 null 이다.
        List<SchedulingPlace> places = List.of(
                place("A", 35.0672, 127.7517),
                place("B", 36.8056, 128.6241),
                place("C", 36.6458, 128.4527));
        given(elevationClient.getElevations(anyString())).willReturn(response(7.0, null));

        // when
        ElevationProfile profile = resolver.resolve(places);

        // then
        assertThat(profile.metersAt(35.0672, 127.7517)).isEqualTo(7.0);
        assertThat(profile.metersAt(36.8056, 128.6241)).isNull();
        assertThat(profile.metersAt(36.6458, 128.4527)).isNull();
    }

    @Test
    @DisplayName("좌표 개수가 상한(100)을 넘으면 앞의 100개만 조회한다.")
    void truncatesToMaxLocations() {
        // given - 서로 다른 좌표 120개
        List<SchedulingPlace> places = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            places.add(place("C" + i, 35.0 + i * 0.001, 127.0));
        }
        given(elevationClient.getElevations(anyString())).willReturn(response(new Double[0]));

        // when
        resolver.resolve(places);

        // then
        verify(elevationClient).getElevations(locationsCaptor.capture());
        assertThat(locationsCaptor.getValue().split("\\|")).hasSize(100);
    }

    @Test
    @DisplayName("좌표가 있는 장소가 없으면 호출하지 않고 고도 미상으로 둔다.")
    void skipsCallWhenNoCoordinates() {
        // given
        List<SchedulingPlace> places = List.of(place("A", null, null), place("B", 35.0, null));

        // when
        ElevationProfile profile = resolver.resolve(places);

        // then
        assertThat(profile.metersByCoordinate()).isEmpty();
        verifyNoInteractions(elevationClient);
    }

    @Test
    @DisplayName("장소 목록이 비었거나 null 이면 호출하지 않는다.")
    void skipsCallWhenPlacesEmpty() {
        // when
        ElevationProfile empty = resolver.resolve(List.of());
        ElevationProfile nullInput = resolver.resolve(null);

        // then
        assertThat(empty.metersByCoordinate()).isEmpty();
        assertThat(nullInput.metersByCoordinate()).isEmpty();
        verifyNoInteractions(elevationClient);
    }
}
