package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.KakaoMobilityClient;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestRequest;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.DistanceBasis;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoadDistanceResolver")
class RoadDistanceResolverTest {

    @Mock private KakaoMobilityClient kakaoMobilityClient;
    @Captor private ArgumentCaptor<KakaoMultiDestRequest> requestCaptor;
    @InjectMocks private RoadDistanceResolver resolver;

    private static final double ORIGIN_LAT = 35.20;
    private static final double ORIGIN_LNG = 127.55;

    private NearbyContentItem candidate(String id, double straightKm) {
        return new NearbyContentItem(
                id, "장소 " + id, "12", "주소", null, 35.21, 127.56,
                ContentCategory.ATTRACTION, null, "HADONG", straightKm);
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
    @DisplayName("모든 경로가 성공하면 도로 거리·소요 시간으로 다시 매기고 도로 거리순으로 정렬한다")
    void allRoutesSucceed_reordersByRoadDistance() {
        // given - 직선거리는 A < B 지만 도로거리는 B < A
        List<NearbyContentItem> candidates = List.of(candidate("A", 1.0), candidate("B", 1.5));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willReturn(response(
                        route("A", 0, 8200, 900),
                        route("B", 0, 2100, 300)
                ));

        // when
        List<NearbyContentItem> result = resolver.resolve(ORIGIN_LAT, ORIGIN_LNG, candidates, 10);

        // then
        assertThat(result).extracting("contentId").containsExactly("B", "A");
        assertThat(result.get(0).distanceKm()).isEqualTo(2.1);
        assertThat(result.get(0).durationMinutes()).isEqualTo(5);
        assertThat(result.get(0).distanceBasis()).isEqualTo(DistanceBasis.ROAD);
        assertThat(result.get(1).distanceKm()).isEqualTo(8.2);
        assertThat(result.get(1).durationMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("일부 목적지 경로가 실패하면 그 항목만 직선거리 기준으로 유지한다")
    void someRoutesFail_thoseKeepStraightDistance() {
        // given
        List<NearbyContentItem> candidates = List.of(candidate("A", 1.0), candidate("B", 3.0));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willReturn(response(
                        route("A", 0, 1500, 240),
                        route("B", 207, null, null)
                ));

        // when
        List<NearbyContentItem> result = resolver.resolve(ORIGIN_LAT, ORIGIN_LNG, candidates, 10);

        // then
        NearbyContentItem a = result.stream().filter(i -> i.contentId().equals("A")).findFirst().orElseThrow();
        NearbyContentItem b = result.stream().filter(i -> i.contentId().equals("B")).findFirst().orElseThrow();
        assertThat(a.distanceBasis()).isEqualTo(DistanceBasis.ROAD);
        assertThat(a.distanceKm()).isEqualTo(1.5);
        assertThat(b.distanceBasis()).isEqualTo(DistanceBasis.STRAIGHT);
        assertThat(b.distanceKm()).isEqualTo(3.0);
        assertThat(b.durationMinutes()).isNull();
    }

    @Test
    @DisplayName("Kakao 호출이 실패하면 예외 없이 직선거리 순서를 유지하고 모두 STRAIGHT 로 표시한다")
    void kakaoThrows_fallsBackToStraightOrder() {
        // given
        List<NearbyContentItem> candidates = List.of(candidate("A", 1.0), candidate("B", 2.0), candidate("C", 3.0));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willThrow(new RuntimeException("503 Service Unavailable"));

        // when
        List<NearbyContentItem> result = resolver.resolve(ORIGIN_LAT, ORIGIN_LNG, candidates, 10);

        // then
        assertThat(result).extracting("contentId").containsExactly("A", "B", "C");
        assertThat(result).allSatisfy(i -> {
            assertThat(i.distanceBasis()).isEqualTo(DistanceBasis.STRAIGHT);
            assertThat(i.durationMinutes()).isNull();
        });
    }

    @Test
    @DisplayName("size 만큼만 반환한다")
    void limitsToSize() {
        // given
        List<NearbyContentItem> candidates = List.of(
                candidate("A", 1.0), candidate("B", 2.0), candidate("C", 3.0));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willReturn(response(
                        route("A", 0, 1000, 120),
                        route("B", 0, 2000, 240),
                        route("C", 0, 3000, 360)
                ));

        // when
        List<NearbyContentItem> result = resolver.resolve(ORIGIN_LAT, ORIGIN_LNG, candidates, 2);

        // then
        assertThat(result).extracting("contentId").containsExactly("A", "B");
    }

    @Test
    @DisplayName("요청에 origin·목적지와 필수 radius(m)를 채운다. radius 는 가장 먼 후보를 덮되 10km 로 제한한다")
    void buildsRequestWithMandatoryRadius() {
        // given
        List<NearbyContentItem> candidates = List.of(candidate("A", 1.0), candidate("B", 4.2));
        given(kakaoMobilityClient.getMultiDestinationDirections(any()))
                .willReturn(response(route("A", 0, 1000, 120), route("B", 0, 5000, 600)));

        // when
        resolver.resolve(ORIGIN_LAT, ORIGIN_LNG, candidates, 10);

        // then
        verify(kakaoMobilityClient).getMultiDestinationDirections(requestCaptor.capture());
        KakaoMultiDestRequest sent = requestCaptor.getValue();
        assertThat(sent.origin().x()).isEqualTo(ORIGIN_LNG);
        assertThat(sent.origin().y()).isEqualTo(ORIGIN_LAT);
        assertThat(sent.destinations()).extracting("key").containsExactly("A", "B");
        assertThat(sent.radius()).isBetween(4200, 10_000);
    }

    @Test
    @DisplayName("후보가 없으면 빈 목록을 반환하고 Kakao 를 호출하지 않는다")
    void emptyCandidates_returnsEmpty() {
        // when
        List<NearbyContentItem> result = resolver.resolve(ORIGIN_LAT, ORIGIN_LNG, List.of(), 10);

        // then
        assertThat(result).isEmpty();
    }
}
