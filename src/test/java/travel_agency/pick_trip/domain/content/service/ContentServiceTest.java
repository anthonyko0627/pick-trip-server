package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapter;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentSummaryResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.repository.projection.NearbyContentProjection;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentService")
class ContentServiceTest {

    @Mock private TourApiContentAdapter adapter;
    @Mock private TravelContentRepository travelContentRepository;
    @Mock private RoadDistanceResolver roadDistanceResolver;
    @InjectMocks private ContentService contentService;

    @Nested
    @DisplayName("getContents")
    class GetContents {

        @Test
        @DisplayName("유효한 region으로 요청하면 ContentListResponse를 반환한다")
        void validRegion_returnsContentListResponse() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, null, null, 0, 20);
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of(
                    new ContentSummaryResponse("123", "쌍계사", 12, "경상남도 하동군", "https://img.jpg", 35.27, 127.58,
                            ContentCategory.ATTRACTION, null, false, "HADONG")
            ));
            given(adapter.fetchList(request, Region.HADONG)).willReturn(expected);

            // when
            ContentListResponse result = contentService.getContents(request);

            // then
            assertThat(result.totalCount()).isEqualTo(1);
            assertThat(result.items().get(0).title()).isEqualTo("쌍계사");
        }

        @Test
        @DisplayName("지원하지 않는 region이면 CONTENT_INVALID_REGION 예외를 던진다")
        void invalidRegion_throwsContentInvalidRegion() {
            // given
            ContentListRequest request = new ContentListRequest("INVALID", null, null, null, null, 0, 20);

            // when & then
            assertThatThrownBy(() -> contentService.getContents(request))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_INVALID_REGION);
        }
    }

    @Nested
    @DisplayName("getContentDetail")
    class GetContentDetail {

        @Test
        @DisplayName("유효한 contentId로 상세 조회 시 ContentDetailResponse를 반환한다")
        void validContentId_returnsContentDetailResponse() {
            // given
            ContentDetailResponse expected = new ContentDetailResponse(
                    "2741429", "쌍계사", 12, "경상남도 하동군", "055-883-1901", "http://ssanggyesa.net",
                    35.27, 127.58, "한국의 4대 총림", "03:00~18:00", "연중무휴",
                    "가능", "성인 3,000원", "불가", "불가",
                    "약 2시간", null, "TourAPI", List.of(),
                    ContentCategory.CULTURE, true, "HADONG"
            );
            given(adapter.fetchDetail("2741429")).willReturn(expected);

            // when
            ContentDetailResponse result = contentService.getContentDetail("2741429");

            // then
            assertThat(result.contentId()).isEqualTo("2741429");
            assertThat(result.title()).isEqualTo("쌍계사");
        }
    }

    @Nested
    @DisplayName("getNearbyContents")
    class GetNearbyContents {

        private static final String ORIGIN_ID = "126508";

        private TravelContent origin(Double latitude, Double longitude) {
            return TravelContent.builder()
                    .sourceContentId(ORIGIN_ID)
                    .title("화개장터")
                    .region(Region.HADONG)
                    .latitude(latitude)
                    .longitude(longitude)
                    .dataStatus(DataStatus.ACTIVE)
                    .build();
        }

        private ContentDetailResponse detailAt(double latitude, double longitude) {
            return new ContentDetailResponse(
                    ORIGIN_ID, "화개장터", 12, "경상남도 하동군", null, null,
                    latitude, longitude, null, null, null, null, null, null, null,
                    null, null, "TourAPI", List.of(), ContentCategory.ATTRACTION, false, "HADONG");
        }

        private NearbyContentResponse.NearbyContentItem remoteItem(String id, double distanceKm) {
            return new NearbyContentResponse.NearbyContentItem(
                    id, "주변 " + id, "12", "하동군", null, 35.1, 127.5,
                    ContentCategory.ATTRACTION, null, "HADONG", distanceKm);
        }

        /** 도로 거리 재정렬은 RoadDistanceResolverTest 가 검증한다. 여기서는 후보를 그대로 통과시킨다. */
        private void stubRoadPassthrough() {
            given(roadDistanceResolver.resolve(anyDouble(), anyDouble(), any(), anyInt()))
                    .willAnswer(invocation -> invocation.getArgument(2));
        }

        @Test
        @DisplayName("로컬 기준 콘텐츠 좌표로 조회한 주변 행이 있으면 LOCAL 소스로 거리순 매핑해 반환한다")
        void localOriginWithNeighbors_returnsLocalSource() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID))
                    .willReturn(Optional.of(origin(35.1234, 127.5678)));
            given(travelContentRepository.findNearby(eq(ORIGIN_ID), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                    .willReturn(List.of(
                            row("222", "최참판댁", "12", "하동군 악양면", "https://img/222.jpg",
                                    35.13, 127.57, "CULTURE", "박경리 토지 배경", "HADONG", 0.98765),
                            row("333", "평사리 들판", "12", "하동군 악양면", null,
                                    35.20, 127.60, null, null, "HADONG", 4.111)
                    ));
            stubRoadPassthrough();

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 5.0, 10);

            // then
            assertThat(result.source()).isEqualTo(NearbyContentResponse.NearbySource.LOCAL);
            assertThat(result.originContentId()).isEqualTo(ORIGIN_ID);
            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).contentId()).isEqualTo("222");
            assertThat(result.items().get(0).category()).isEqualTo(ContentCategory.CULTURE);
            assertThat(result.items().get(0).distanceKm()).isEqualTo(0.99);
            assertThat(result.items().get(1).category()).isNull();
            assertThat(result.items().get(1).distanceKm()).isEqualTo(4.11);
        }

        @Test
        @DisplayName("radius와 size가 상한을 넘으면 각각 20km, 30개로 잘라 조회한다")
        void radiusAndSizeClampedToMax() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID))
                    .willReturn(Optional.of(origin(35.1234, 127.5678)));
            given(travelContentRepository.findNearby(any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                    .willReturn(List.of(
                            row("222", "x", "12", "a", null, 35.1, 127.5, null, null, "HADONG", 1.0)));

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 999.0, 999);

            // then
            verify(travelContentRepository).findNearby(ORIGIN_ID, 35.1234, 127.5678, 20.0, 30);
            assertThat(result.radiusKm()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("radius와 size가 0 이하이면 기본값 5km, 10개로 조회한다")
        void nonPositiveRadiusAndSizeFallBackToDefaults() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID))
                    .willReturn(Optional.of(origin(35.1234, 127.5678)));
            given(travelContentRepository.findNearby(any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                    .willReturn(List.of(
                            row("222", "x", "12", "a", null, 35.1, 127.5, null, null, "HADONG", 1.0)));

            // when
            contentService.getNearbyContents(ORIGIN_ID, 0.0, 0);

            // then
            verify(travelContentRepository).findNearby(ORIGIN_ID, 35.1234, 127.5678, 5.0, 10);
        }

        @Test
        @DisplayName("로컬 기준 콘텐츠는 있으나 주변 로컬 행이 없으면 TourAPI 폴백 결과를 TOURAPI 소스로 반환한다")
        void localOriginButNoLocalNeighbors_fallsBackToTourApi() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID))
                    .willReturn(Optional.of(origin(35.1234, 127.5678)));
            given(travelContentRepository.findNearby(eq(ORIGIN_ID), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                    .willReturn(List.of());
            given(adapter.fetchNearby(ORIGIN_ID, 35.1234, 127.5678, 5.0, 10))
                    .willReturn(List.of(remoteItem("999", 2.5)));
            stubRoadPassthrough();

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 5.0, 10);

            // then
            assertThat(result.source()).isEqualTo(NearbyContentResponse.NearbySource.TOURAPI);
            assertThat(result.items()).extracting("contentId").containsExactly("999");
        }

        @Test
        @DisplayName("기준 콘텐츠가 로컬에 없으면 TourAPI 상세로 좌표를 확보해 폴백한다")
        void originNotInLocalDb_resolvesCoordinatesFromDetailAndFallsBack() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID)).willReturn(Optional.empty());
            given(adapter.fetchDetail(ORIGIN_ID)).willReturn(detailAt(35.15, 127.55));
            given(adapter.fetchNearby(ORIGIN_ID, 35.15, 127.55, 5.0, 10))
                    .willReturn(List.of(remoteItem("999", 1.0)));
            stubRoadPassthrough();

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 5.0, 10);

            // then
            assertThat(result.source()).isEqualTo(NearbyContentResponse.NearbySource.TOURAPI);
            assertThat(result.items()).hasSize(1);
            verify(travelContentRepository, never())
                    .findNearby(any(), anyDouble(), anyDouble(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("로컬에 없고 TourAPI 상세도 해당 콘텐츠를 모르면 CONTENT_NOT_FOUND를 전파한다")
        void originNotInLocalDb_andDetailNotFound_propagatesContentNotFound() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID)).willReturn(Optional.empty());
            given(adapter.fetchDetail(ORIGIN_ID))
                    .willThrow(new ContentException(ErrorCode.CONTENT_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> contentService.getNearbyContents(ORIGIN_ID, 5.0, 10))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);
        }

        @Test
        @DisplayName("로컬 좌표가 없으면 TourAPI 상세 좌표로 폴백한다")
        void localOriginWithoutCoordinates_resolvesFromDetailAndFallsBack() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID))
                    .willReturn(Optional.of(origin(null, null)));
            given(adapter.fetchDetail(ORIGIN_ID)).willReturn(detailAt(35.15, 127.55));
            given(adapter.fetchNearby(ORIGIN_ID, 35.15, 127.55, 5.0, 10))
                    .willReturn(List.of(remoteItem("999", 1.0)));
            stubRoadPassthrough();

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 5.0, 10);

            // then
            assertThat(result.source()).isEqualTo(NearbyContentResponse.NearbySource.TOURAPI);
        }

        @Test
        @DisplayName("로컬·TourAPI 상세 모두 좌표가 없으면 CONTENT_LOCATION_UNKNOWN을 던진다")
        void neitherLocalNorDetailHasCoordinates_throwsLocationUnknown() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID))
                    .willReturn(Optional.of(origin(null, null)));
            given(adapter.fetchDetail(ORIGIN_ID)).willReturn(detailAt(0.0, 0.0));

            // when & then
            assertThatThrownBy(() -> contentService.getNearbyContents(ORIGIN_ID, 5.0, 10))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_LOCATION_UNKNOWN);
        }

        @Test
        @DisplayName("기준 콘텐츠 좌표가 (0, 0)이면 TourAPI 상세 좌표로 폴백을 시도한다")
        void originAtNullIsland_resolvesFromDetail() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID))
                    .willReturn(Optional.of(origin(0.0, 0.0)));
            given(adapter.fetchDetail(ORIGIN_ID)).willReturn(detailAt(35.15, 127.55));
            given(adapter.fetchNearby(ORIGIN_ID, 35.15, 127.55, 5.0, 10)).willReturn(List.of());

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 5.0, 10);

            // then
            assertThat(result.source()).isEqualTo(NearbyContentResponse.NearbySource.TOURAPI);
            assertThat(result.items()).isEmpty();
        }

        @Test
        @DisplayName("TourAPI 폴백 결과가 비어도 200 응답으로 빈 목록을 TOURAPI 소스로 반환한다")
        void tourApiFallbackEmpty_returnsEmptyItems() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID)).willReturn(Optional.empty());
            given(adapter.fetchDetail(ORIGIN_ID)).willReturn(detailAt(35.15, 127.55));
            given(adapter.fetchNearby(ORIGIN_ID, 35.15, 127.55, 5.0, 10)).willReturn(List.of());

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 5.0, 10);

            // then
            assertThat(result.items()).isEmpty();
            assertThat(result.source()).isEqualTo(NearbyContentResponse.NearbySource.TOURAPI);
        }

        @Test
        @DisplayName("후보를 기준 좌표와 함께 RoadDistanceResolver 로 넘기고, 재정렬된 결과를 응답으로 반환한다")
        void passesCandidatesThroughRoadResolver() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID))
                    .willReturn(Optional.of(origin(35.1234, 127.5678)));
            given(travelContentRepository.findNearby(eq(ORIGIN_ID), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                    .willReturn(List.of(
                            row("222", "가", "12", "주소", null, 35.13, 127.57, null, null, "HADONG", 1.0),
                            row("333", "나", "12", "주소", null, 35.20, 127.60, null, null, "HADONG", 2.0)
                    ));
            // resolver 가 도로 거리로 순서를 뒤집었다고 가정
            given(roadDistanceResolver.resolve(eq(35.1234), eq(127.5678), any(), eq(10)))
                    .willReturn(List.of(remoteItem("333", 1.2), remoteItem("222", 3.4)));

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 5.0, 10);

            // then
            assertThat(result.items()).extracting("contentId").containsExactly("333", "222");
        }

        private NearbyContentProjection row(
                String id, String title, String contentTypeId, String address, String firstImage,
                double latitude, double longitude, String category, String summary, String region, double distanceKm
        ) {
            return new NearbyContentProjection() {
                @Override public String getSourceContentId() { return id; }
                @Override public String getContentTypeId() { return contentTypeId; }
                @Override public String getTitle() { return title; }
                @Override public String getAddress() { return address; }
                @Override public String getFirstImage() { return firstImage; }
                @Override public Double getLatitude() { return latitude; }
                @Override public Double getLongitude() { return longitude; }
                @Override public String getCategory() { return category; }
                @Override public String getSummary() { return summary; }
                @Override public String getRegion() { return region; }
                @Override public Double getDistanceKm() { return distanceKm; }
            };
        }
    }
}
