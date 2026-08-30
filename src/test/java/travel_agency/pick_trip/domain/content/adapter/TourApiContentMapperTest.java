package travel_agency.pick_trip.domain.content.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.region.Region;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TourApiContentMapper")
class TourApiContentMapperTest {

    private TourApiContentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TourApiContentMapper();
    }

    @Nested
    @DisplayName("toListResponse")
    class ToListResponse {

        @Test
        @DisplayName("정상적인 TourAPI 목록 응답을 ContentListResponse로 변환한다")
        void validResponse_mapsToContentListResponse() {
            // given
            // TourApiListResponse.Item 필드 순서: contentid, contenttypeid, title, addr1, addr2, mapx, mapy, firstimage, firstimage2, lclsSystm1, lclsSystm2
            TourApiListResponse.Item item = new TourApiListResponse.Item(
                    "2741429", "12", "쌍계사",
                    "경상남도 하동군 화개면 쌍계사길 59", "",
                    "127.581783", "35.273185",
                    "https://example.com/img.jpg", "",
                    "HS", "HS01"
            );
            TourApiListResponse raw = new TourApiListResponse(
                    new TourApiListResponse.Response(
                            new TourApiListResponse.Body(
                                    new TourApiListResponse.Items(List.of(item)),
                                    20, 1, 150
                            )
                    )
            );

            // when
            ContentListResponse result = mapper.toListResponse(raw, 0, 20, Region.HADONG);

            // then
            assertThat(result.totalCount()).isEqualTo(150);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).contentId()).isEqualTo("2741429");
            assertThat(result.items().get(0).title()).isEqualTo("쌍계사");
            // mapy → latitude(위도), mapx → longitude(경도)
            assertThat(result.items().get(0).latitude()).isEqualTo(35.273185);
            assertThat(result.items().get(0).longitude()).isEqualTo(127.581783);
            // lclsSystm1=HS(역사관광) → CULTURE, region은 요청 파라미터를 그대로 echo
            assertThat(result.items().get(0).category()).isEqualTo(ContentCategory.CULTURE);
            assertThat(result.items().get(0).indoor()).isTrue();
            assertThat(result.items().get(0).region()).isEqualTo("HADONG");
            assertThat(result.items().get(0).summary()).isNull();
        }

        @Test
        @DisplayName("items.item이 null이면 빈 목록을 반환한다")
        void nullItems_returnsEmptyList() {
            // given
            TourApiListResponse raw = new TourApiListResponse(
                    new TourApiListResponse.Response(
                            new TourApiListResponse.Body(
                                    new TourApiListResponse.Items(null),
                                    20, 1, 0
                            )
                    )
            );

            // when
            ContentListResponse result = mapper.toListResponse(raw, 0, 20, Region.HADONG);

            // then
            assertThat(result.items()).isEmpty();
            assertThat(result.totalCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("toDetailResponse")
    class ToDetailResponse {

        @Test
        @DisplayName("세 API 응답을 병합해 ContentDetailResponse를 반환한다")
        void mergesThreeResponses() {
            // given
            // TourApiDetailCommonResponse.Item 필드 순서: contentid, contenttypeid, title, addr1, addr2, tel, homepage, mapx, mapy, firstimage, overview, lclsSystm1, lclsSystm2, lclsSystm3, lDongRegnCd, lDongSignguCd
            TourApiDetailCommonResponse common = new TourApiDetailCommonResponse(
                    new TourApiDetailCommonResponse.Response(
                            new TourApiDetailCommonResponse.Body(
                                    new TourApiDetailCommonResponse.Items(List.of(
                                            new TourApiDetailCommonResponse.Item(
                                                    "2741429", "12", "쌍계사",
                                                    "경상남도 하동군 화개면", "",
                                                    "055-883-1901", "http://ssanggyesa.net",
                                                    "127.58", "35.27",
                                                    "https://img.jpg", "한국의 4대 총림",
                                                    "HS", "HS01", "HS010600",
                                                    "48", "850"
                                            )
                                    ))
                            )
                    )
            );
            // TourApiDetailIntroResponse.Item 필드 순서: contentid, contenttypeid,
            // usetime, restdate, usetimeculture, restdateculture, playtime,
            // usetimeleports, restdateleports, opentime, restdateshopping,
            // opentimefood, restdatefood, parking, usefee, chkbabycarriage, chkpet
            TourApiDetailIntroResponse intro = new TourApiDetailIntroResponse(
                    new TourApiDetailIntroResponse.Response(
                            new TourApiDetailIntroResponse.Body(
                                    new TourApiDetailIntroResponse.Items(List.of(
                                            new TourApiDetailIntroResponse.Item(
                                                    "2741429", "12",
                                                    "03:00~18:00", "연중무휴", "가능",
                                                    null, null, null,
                                                    null,
                                                    null, null, null,
                                                    null, null, null, null,
                                                    null, null, null,
                                                    "성인 3,000원",
                                                    "불가", "불가"
                                            )
                                    ))
                            )
                    )
            );
            // TourApiDetailImageResponse.Item 필드 순서: contentid, originimgurl, imgname
            TourApiDetailImageResponse image = new TourApiDetailImageResponse(
                    new TourApiDetailImageResponse.Response(
                            new TourApiDetailImageResponse.Body(
                                    new TourApiDetailImageResponse.Items(List.of(
                                            new TourApiDetailImageResponse.Item(
                                                    "2741429", "https://img1.jpg", "대웅전"
                                            )
                                    ))
                            )
                    )
            );

            // when
            ContentDetailResponse result = mapper.toDetailResponse(common, intro, image);

            // then
            assertThat(result.contentId()).isEqualTo("2741429");
            assertThat(result.summary()).isEqualTo("한국의 4대 총림");
            assertThat(result.useTime()).isEqualTo("03:00~18:00");
            assertThat(result.parking()).isEqualTo("가능");
            assertThat(result.stayDuration()).isEqualTo("약 2시간"); // contentTypeId=12 (관광지)
            assertThat(result.reservationRequired()).isNull();
            assertThat(result.dataSource()).isEqualTo("TourAPI");
            assertThat(result.images()).hasSize(1);
            assertThat(result.images().get(0).imageUrl()).isEqualTo("https://img1.jpg");
            // lclsSystm1=HS(역사관광) → CULTURE, lDongRegnCd=48/lDongSignguCd=850 → HADONG
            assertThat(result.category()).isEqualTo(ContentCategory.CULTURE);
            assertThat(result.indoor()).isTrue();
            assertThat(result.region()).isEqualTo("HADONG");
        }

        @Test
        @DisplayName("법정동 코드만 달린 콘텐츠도 지역을 역매핑한다")
        void ldongCodeOnly_resolvesRegion() {
            // given - 부석사(127669): TourAPI가 legacy areacode/sigungucode를 비우고 법정동 코드만 채워 내려준다
            TourApiDetailCommonResponse common = new TourApiDetailCommonResponse(
                    new TourApiDetailCommonResponse.Response(
                            new TourApiDetailCommonResponse.Body(
                                    new TourApiDetailCommonResponse.Items(List.of(
                                            new TourApiDetailCommonResponse.Item(
                                                    "127669", "12", "부석사",
                                                    "경상북도 영주시 부석면 부석사로 345", "",
                                                    "054-633-3464", "",
                                                    "128.68", "36.99",
                                                    "https://img.jpg", "신라 문무왕 때 창건한 사찰",
                                                    "HS", "HS01", "HS010600",
                                                    "47", "210"
                                            )
                                    ))
                            )
                    )
            );

            // when
            ContentDetailResponse result = mapper.toDetailResponse(
                    common,
                    new TourApiDetailIntroResponse(null),
                    new TourApiDetailImageResponse(null));

            // then
            assertThat(result.region()).isEqualTo("YEONGJU");
        }
    }
}
