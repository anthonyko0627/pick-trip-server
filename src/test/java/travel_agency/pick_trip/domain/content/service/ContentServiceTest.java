package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

        @Test
        @DisplayName("기준 콘텐츠가 없으면 CONTENT_NOT_FOUND 예외를 던진다")
        void originNotFound_throwsContentNotFound() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> contentService.getNearbyContents(ORIGIN_ID, 5.0, 10))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);
        }

        @Test
        @DisplayName("기준 콘텐츠에 좌표가 없으면 CONTENT_LOCATION_UNKNOWN 예외를 던진다")
        void originWithoutCoordinates_throwsContentLocationUnknown() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID)).willReturn(Optional.of(origin(null, null)));

            // when & then
            assertThatThrownBy(() -> contentService.getNearbyContents(ORIGIN_ID, 5.0, 10))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_LOCATION_UNKNOWN);
        }

        @Test
        @DisplayName("기준 콘텐츠 좌표가 (0, 0)이면 CONTENT_LOCATION_UNKNOWN 예외를 던진다")
        void originAtNullIsland_throwsContentLocationUnknown() {
            // given
            given(travelContentRepository.findById(ORIGIN_ID)).willReturn(Optional.of(origin(0.0, 0.0)));

            // when & then
            assertThatThrownBy(() -> contentService.getNearbyContents(ORIGIN_ID, 5.0, 10))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_LOCATION_UNKNOWN);
        }

        @Test
        @DisplayName("기준 좌표로 조회한 행을 거리 오름차순 아이템으로 매핑하고 거리를 소수 2자리로 반올림한다")
        void validOrigin_mapsRowsWithRoundedDistance() {
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

            // when
            NearbyContentResponse result = contentService.getNearbyContents(ORIGIN_ID, 5.0, 10);

            // then
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
                    .willReturn(List.of());

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
                    .willReturn(List.of());

            // when
            contentService.getNearbyContents(ORIGIN_ID, 0.0, 0);

            // then
            verify(travelContentRepository).findNearby(ORIGIN_ID, 35.1234, 127.5678, 5.0, 10);
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
