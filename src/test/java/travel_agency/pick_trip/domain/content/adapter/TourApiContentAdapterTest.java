package travel_agency.pick_trip.domain.content.adapter;

import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiLocationListResponse;
import travel_agency.pick_trip.domain.content.dto.request.CompanionType;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TourApiContentAdapter")
class TourApiContentAdapterTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private TourApiContentMapper mapper;
    @InjectMocks private TourApiContentAdapter adapter;

    @Nested
    @DisplayName("fetchList - 키워드 없음")
    class FetchListWithoutKeyword {

        @Test
        @DisplayName("keyword가 없으면 areaBasedList2를 호출한다")
        void noKeyword_callsAreaBasedList() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, null, null, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getLDongRegnCd()),
                    eq(region.getLDongSignguCd()),
                    isNull(),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20, region)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("fetchList - 키워드 있음")
    class FetchListWithKeyword {

        @Test
        @DisplayName("keyword가 있으면 searchKeyword2를 호출한다")
        void withKeyword_callsSearchKeyword() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, "쌍계사", null, null, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of());

            given(tourApiClient.searchByKeyword(
                    eq("쌍계사"),
                    eq(region.getLDongRegnCd()),
                    eq(region.getLDongSignguCd()),
                    isNull(),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20, region)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("fetchList - TourAPI 오류 응답")
    class FetchListWithErrorResponse {

        @Test
        @DisplayName("resultCode가 오류이면 CONTENT_PROVIDER_FAILED를 던진다")
        void errorResultCode_throwsContentProviderFailed() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, null, null, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse errorResponse = errorListResponse();

            given(tourApiClient.getAreaBasedList(
                    eq(region.getLDongRegnCd()),
                    eq(region.getLDongSignguCd()),
                    isNull(),
                    eq(1),
                    eq(20)
            )).willReturn(errorResponse);

            // when & then
            assertThatThrownBy(() -> adapter.fetchList(request, region))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }

    @Nested
    @DisplayName("fetchList - 필터 변환")
    class FetchListWithFilter {

        @Test
        @DisplayName("indoorOnly=true이면 contentTypeId=14로 areaBasedList2를 호출한다")
        void indoorOnly_true_callsWithCultureContentTypeId() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, null, true, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getLDongRegnCd()),
                    eq(region.getLDongSignguCd()),
                    eq("14"),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20, region)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("indoorOnly=false이면 contentTypeId=12로 areaBasedList2를 호출한다")
        void indoorOnly_false_callsWithTourismContentTypeId() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, null, false, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getLDongRegnCd()),
                    eq(region.getLDongSignguCd()),
                    eq("12"),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20, region)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("companion=FAMILY이면 contentTypeId=14로 areaBasedList2를 호출한다")
        void companionFamily_callsWithCultureContentTypeId() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, CompanionType.FAMILY, null, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getLDongRegnCd()),
                    eq(region.getLDongSignguCd()),
                    eq("14"),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20, region)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("explicit contentTypeId가 있으면 indoorOnly와 companion을 무시한다")
        void explicitContentTypeId_ignoresFilters() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", "28", null, CompanionType.FAMILY, true, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getLDongRegnCd()),
                    eq(region.getLDongSignguCd()),
                    eq("28"),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20, region)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("fetchDetail")
    class FetchDetail {

        @Test
        @DisplayName("TourAPI 호출 실패 시 CONTENT_PROVIDER_FAILED 예외를 던진다")
        void feignException_throwsContentProviderFailed() {
            // given
            given(tourApiClient.getDetailCommon(any()))
                    .willThrow(FeignException.class);

            // when & then
            assertThatThrownBy(() -> adapter.fetchDetail("2741429"))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_PROVIDER_FAILED);
        }

        @Test
        @DisplayName("resultCode가 오류이면 CONTENT_NOT_FOUND가 아니라 CONTENT_PROVIDER_FAILED를 던진다")
        void errorResultCode_throwsContentProviderFailedNotNotFound() {
            // given - 오류 응답은 items도 비어 있으므로, 오류 검사가 없으면 NOT_FOUND로 오분류된다
            given(tourApiClient.getDetailCommon("2741429")).willReturn(errorCommonResponse());

            // when & then
            assertThatThrownBy(() -> adapter.fetchDetail("2741429"))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }

    @Nested
    @DisplayName("fetchNearby")
    class FetchNearby {

        @Test
        @DisplayName("좌표·반경으로 locationBasedList2를 거리순으로 호출하고 매핑 결과를 반환한다")
        void callsLocationBasedListAndMaps() {
            // given
            TourApiLocationListResponse raw = emptyLocationResponse();
            List<NearbyContentItem> mapped = List.of(nearbyItem("222", 1.2));
            given(tourApiClient.getLocationBasedList("127.5678", "35.1234", 5000, "E", 1, 20))
                    .willReturn(raw);
            given(mapper.toNearbyItems(raw, "111", 10)).willReturn(mapped);

            // when
            List<NearbyContentItem> result = adapter.fetchNearby("111", 35.1234, 127.5678, 5.0, 10);

            // then
            assertThat(result).isEqualTo(mapped);
        }

        @Test
        @DisplayName("반경이 20km를 넘으면 20000m로 제한해 호출한다")
        void radiusCappedAt20km() {
            // given
            TourApiLocationListResponse raw = emptyLocationResponse();
            given(tourApiClient.getLocationBasedList(eq("127.0"), eq("35.0"), eq(20000), eq("E"), eq(1), anyInt()))
                    .willReturn(raw);
            given(mapper.toNearbyItems(eq(raw), eq("111"), anyInt())).willReturn(List.of());

            // when
            adapter.fetchNearby("111", 35.0, 127.0, 999.0, 10);

            // then
            verify(tourApiClient).getLocationBasedList("127.0", "35.0", 20000, "E", 1, 20);
        }

        @Test
        @DisplayName("resultCode가 오류이면 CONTENT_PROVIDER_FAILED를 던진다")
        void errorResultCode_throwsProviderFailed() {
            // given
            given(tourApiClient.getLocationBasedList(any(), any(), anyInt(), any(), anyInt(), anyInt()))
                    .willReturn(errorLocationResponse());

            // when & then
            assertThatThrownBy(() -> adapter.fetchNearby("111", 35.0, 127.0, 5.0, 10))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_PROVIDER_FAILED);
        }

        @Test
        @DisplayName("TourAPI 호출이 실패하면 CONTENT_PROVIDER_FAILED를 던진다")
        void feignException_throwsProviderFailed() {
            // given
            given(tourApiClient.getLocationBasedList(any(), any(), anyInt(), any(), anyInt(), anyInt()))
                    .willThrow(FeignException.class);

            // when & then
            assertThatThrownBy(() -> adapter.fetchNearby("111", 35.0, 127.0, 5.0, 10))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_PROVIDER_FAILED);
        }

        private NearbyContentItem nearbyItem(String id, double distanceKm) {
            return new NearbyContentItem(
                    id, "t" + id, "12", "addr", null, 35.1, 127.5,
                    ContentCategory.ATTRACTION, null, "HADONG", distanceKm);
        }
    }

    private TourApiLocationListResponse emptyLocationResponse() {
        return new TourApiLocationListResponse(
                new TourApiLocationListResponse.Response(
                        new TourApiLocationListResponse.Body(
                                new TourApiLocationListResponse.Items(List.of()), 20, 1, 0)));
    }

    private TourApiLocationListResponse errorLocationResponse() {
        return new TourApiLocationListResponse(
                new TourApiLocationListResponse.Response(
                        new TourApiLocationListResponse.Header("30", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"),
                        null));
    }

    private TourApiListResponse emptyListResponse() {
        return new TourApiListResponse(
                new TourApiListResponse.Response(
                        new TourApiListResponse.Body(
                                new TourApiListResponse.Items(List.of()),
                                20, 1, 0
                        )
                )
        );
    }

    private TourApiListResponse errorListResponse() {
        return new TourApiListResponse(
                new TourApiListResponse.Response(
                        new TourApiListResponse.Header("30", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"),
                        null
                )
        );
    }

    private TourApiDetailCommonResponse errorCommonResponse() {
        return new TourApiDetailCommonResponse(
                new TourApiDetailCommonResponse.Response(
                        new TourApiDetailCommonResponse.Header("30", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"),
                        null
                )
        );
    }
}
