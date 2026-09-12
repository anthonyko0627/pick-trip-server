package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.KakaoMobilityClient;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestResponse;
import travel_agency.pick_trip.domain.itinerary.scheduling.OperatingHours;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPlace;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPolicy;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMatrix;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoadMatrixResolver")
class RoadMatrixResolverTest {

    @Mock private KakaoMobilityClient kakaoMobilityClient;
    @InjectMocks private RoadMatrixResolver resolver;

    private SchedulingPlace place(String contentId, double latitude) {
        return new SchedulingPlace(
                contentId, "장소 " + contentId, null, latitude, 127.5, OperatingHours.unknown(), 90, false);
    }

    private SchedulingPlace noCoordinates(String contentId) {
        return new SchedulingPlace(
                contentId, "장소 " + contentId, null, null, null, OperatingHours.unknown(), 90, false);
    }

    private KakaoMultiDestResponse response(KakaoMultiDestResponse.Route... routes) {
        return new KakaoMultiDestResponse(List.of(routes));
    }

    private KakaoMultiDestResponse.Route route(String key, int resultCode, Integer distanceM, Integer durationS) {
        KakaoMultiDestResponse.Summary summary =
                distanceM == null ? null : new KakaoMultiDestResponse.Summary(distanceM, durationS);
        return new KakaoMultiDestResponse.Route(key, resultCode, summary);
    }

    @Test
    @DisplayName("장소마다 1콜씩 호출해 방향별 도로 거리·시간을 담는다")
    void callsOncePerOriginAndFillsBothDirections() {
        // given
        List<SchedulingPlace> places = List.of(place("a", 35.0), place("b", 35.1));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willReturn(response(route("b", 0, 13_000, 1_200)))
                .willReturn(response(route("a", 0, 12_000, 1_200)));

        // when
        TravelMatrix matrix = resolver.resolve(places);

        // then
        verify(kakaoMobilityClient, times(2)).getMultiDestinationDirections(any());
        assertThat(matrix.leg("a", "b").km()).isEqualTo(13.0);
        assertThat(matrix.leg("a", "b").minutes()).isEqualTo(20);
        assertThat(matrix.leg("b", "a").km()).isEqualTo(12.0);
    }

    @Test
    @DisplayName("실측에 성공한 구간의 평균 속도를 폴백 환산 속도로 남긴다")
    void keepsObservedSpeedForFallback() {
        // given - 25km 를 30분에 갔으므로 관측 속도는 50km/h
        List<SchedulingPlace> places = List.of(place("a", 35.0), place("b", 35.1));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willReturn(response(route("b", 0, 25_000, 1_800)))
                .willReturn(response());

        // when
        TravelMatrix matrix = resolver.resolve(places);

        // then
        assertThat(matrix.fallbackSpeedKmh()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("경로를 찾지 못한 목적지는 행렬에서 빠지고 나머지만 담긴다")
    void skipsFailedRoutes() {
        // given
        List<SchedulingPlace> places = List.of(place("a", 35.0), place("b", 35.1), place("c", 35.2));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willReturn(response(route("b", 0, 13_000, 1_200), route("c", 104, null, null)))
                .willReturn(response())
                .willReturn(response());

        // when
        TravelMatrix matrix = resolver.resolve(places);

        // then
        assertThat(matrix.leg("a", "b")).isNotNull();
        assertThat(matrix.leg("a", "c")).isNull();
    }

    @Test
    @DisplayName("길찾기가 실패하면 예외 없이 빈 행렬을 돌려준다")
    void returnsEmptyMatrixWhenDirectionsFail() {
        // given
        List<SchedulingPlace> places = List.of(place("a", 35.0), place("b", 35.1));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willThrow(new IllegalStateException("timeout"));

        // when
        TravelMatrix matrix = resolver.resolve(places);

        // then
        assertThat(matrix.legs()).isEmpty();
        assertThat(matrix.fallbackSpeedKmh()).isEqualTo(SchedulingPolicy.AVG_SPEED_KMH);
        // 첫 콜이 실패하면 남은 콜은 태우지 않는다.
        verify(kakaoMobilityClient, times(1)).getMultiDestinationDirections(any());
    }

    @Test
    @DisplayName("좌표를 가진 장소가 두 곳 미만이면 길찾기를 호출하지 않는다")
    void doesNotCallWhenTooFewCoordinates() {
        // given
        List<SchedulingPlace> places = List.of(place("a", 35.0), noCoordinates("b"));

        // when
        TravelMatrix matrix = resolver.resolve(places);

        // then
        assertThat(matrix.legs()).isEmpty();
        verify(kakaoMobilityClient, never()).getMultiDestinationDirections(any());
    }
}
