package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentCollectService")
class ContentCollectServiceTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private TravelContentRepository travelContentRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private ContentCollectService contentCollectService;

    private static final Region REGION = Region.HADONG; // areaCode=36, sigunguCode=18
    private static final String CONTENT_ID = "126508";

    @BeforeEach
    void setUp() {
        // ContentCollectMapper 는 순수 파싱 로직이므로 실제 인스턴스를 사용한다.
        contentCollectService = new ContentCollectService(
                tourApiClient, travelContentRepository, new ContentCollectMapper(), transactionTemplate);
        // executeWithoutResult 는 콜백(영속화)을 즉시 실행하도록 스텁한다.
        lenient().doAnswer(inv -> {
            inv.getArgument(0, Consumer.class).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    // --- 테스트 헬퍼 ---

    private TourApiListResponse listResponse(TourApiListResponse.Item... items) {
        return new TourApiListResponse(new TourApiListResponse.Response(
                new TourApiListResponse.Body(new TourApiListResponse.Items(List.of(items)), 100, 1, items.length)));
    }

    private TourApiListResponse.Item listItem() {
        return new TourApiListResponse.Item(
                CONTENT_ID, "12", "화개장터", "경남 하동군", "화개면", "127.7", "35.1", "list.jpg", "list2.jpg",
                null, null);
    }

    private TourApiDetailCommonResponse commonResponse() {
        return commonResponse(null, null, null);
    }

    private TourApiDetailCommonResponse commonResponse(String lclsSystm1, String lclsSystm2) {
        return commonResponse(lclsSystm1, lclsSystm2, null);
    }

    private TourApiDetailCommonResponse commonResponse(String lclsSystm1, String lclsSystm2, String lclsSystm3) {
        return new TourApiDetailCommonResponse(new TourApiDetailCommonResponse.Response(
                new TourApiDetailCommonResponse.Body(new TourApiDetailCommonResponse.Items(List.of(
                        new TourApiDetailCommonResponse.Item(
                                CONTENT_ID, "12", "화개장터", "경남 하동군 화개면", "탑리", "055-000-0000",
                                "http://hwagae.kr", "127.7", "35.1", "common.jpg", "지리산 자락의 전통 장터",
                                lclsSystm1, lclsSystm2, lclsSystm3, "36", "18"))))));
    }

    private TourApiDetailIntroResponse introResponse() {
        return new TourApiDetailIntroResponse(new TourApiDetailIntroResponse.Response(
                new TourApiDetailIntroResponse.Body(new TourApiDetailIntroResponse.Items(List.of(
                        new TourApiDetailIntroResponse.Item(
                                CONTENT_ID, "12", "09:00~18:00", "연중무휴",
                                // 14/15/28/38/39 타입 전용 필드 (관광지 응답에는 비어 온다)
                                null, null, null, null, null, null, null, null, null,
                                "가능", "무료", "가능", "불가"))))));
    }

    private TourApiDetailImageResponse imageResponse() {
        return new TourApiDetailImageResponse(new TourApiDetailImageResponse.Response(
                new TourApiDetailImageResponse.Body(new TourApiDetailImageResponse.Items(List.of(
                        new TourApiDetailImageResponse.Item(CONTENT_ID, "detail1.jpg", "전경"))))));
    }

    private void stubDetailCalls() {
        given(tourApiClient.getDetailCommon(CONTENT_ID)).willReturn(commonResponse());
        given(tourApiClient.getDetailIntro(CONTENT_ID, "12")).willReturn(introResponse());
        given(tourApiClient.getDetailImage(CONTENT_ID)).willReturn(imageResponse());
    }

    private FeignException feignError() {
        return new FeignException(500, "tour-api 5xx") {};
    }

    @Test
    @DisplayName("신규 콘텐츠를 상세 보강과 함께 저장한다")
    void collectRegion_신규콘텐츠_저장() {
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.empty());
        stubDetailCalls();

        int collected = contentCollectService.collectRegion(REGION);

        assertThat(collected).isEqualTo(1);

        ArgumentCaptor<TravelContent> captor = ArgumentCaptor.forClass(TravelContent.class);
        verify(travelContentRepository, times(1)).save(captor.capture());

        TravelContent saved = captor.getValue();
        assertThat(saved.getSourceContentId()).isEqualTo(CONTENT_ID);
        assertThat(saved.getRegion()).isEqualTo(Region.HADONG);
        assertThat(saved.getSummary()).isEqualTo("지리산 자락의 전통 장터");
        assertThat(saved.getAddress()).isEqualTo("경남 하동군 화개면 탑리");
        assertThat(saved.getDetail()).isNotNull();
        assertThat(saved.getDetail().getUseTime()).isEqualTo("09:00~18:00");
        assertThat(saved.getImages()).hasSize(1);
        assertThat(saved.getImages().get(0).getImageUrl()).isEqualTo("detail1.jpg");
    }

    @Test
    @DisplayName("신분류체계 대분류 코드를 6종 category로 변환한다")
    void collectRegion_신분류체계_대분류_매핑() {
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.empty());
        given(tourApiClient.getDetailCommon(CONTENT_ID)).willReturn(commonResponse("NA", "NA05"));
        given(tourApiClient.getDetailIntro(CONTENT_ID, "12")).willReturn(introResponse());
        given(tourApiClient.getDetailImage(CONTENT_ID)).willReturn(imageResponse());

        contentCollectService.collectRegion(REGION);

        ArgumentCaptor<TravelContent> captor = ArgumentCaptor.forClass(TravelContent.class);
        verify(travelContentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(ContentCategory.NATURE);
    }

    @Test
    @DisplayName("VE(문화관광) 중분류는 대분류 기본값 대신 개별 예외 매핑을 사용한다")
    void collectRegion_신분류체계_VE_예외매핑() {
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.empty());
        given(tourApiClient.getDetailCommon(CONTENT_ID)).willReturn(commonResponse("VE", "VE03"));
        given(tourApiClient.getDetailIntro(CONTENT_ID, "12")).willReturn(introResponse());
        given(tourApiClient.getDetailImage(CONTENT_ID)).willReturn(imageResponse());

        contentCollectService.collectRegion(REGION);

        ArgumentCaptor<TravelContent> captor = ArgumentCaptor.forClass(TravelContent.class);
        verify(travelContentRepository, times(1)).save(captor.capture());
        // VE 대분류 기본값은 ATTRACTION 이지만 VE03(도시공원)은 NATURE 로 예외 매핑된다.
        assertThat(captor.getValue().getCategory()).isEqualTo(ContentCategory.NATURE);
    }

    @Test
    @DisplayName("신분류체계 코드가 없으면 ATTRACTION으로 대체한다")
    void collectRegion_신분류체계_코드없음_기본값() {
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.empty());
        stubDetailCalls();

        contentCollectService.collectRegion(REGION);

        ArgumentCaptor<TravelContent> captor = ArgumentCaptor.forClass(TravelContent.class);
        verify(travelContentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(ContentCategory.ATTRACTION);
    }

    @Test
    @DisplayName("기존 콘텐츠는 상세 정보를 갱신한다")
    void collectRegion_기존콘텐츠_갱신() {
        TravelContent existing = TravelContent.builder()
                .sourceContentId(CONTENT_ID)
                .contentTypeId("12")
                .title("옛 제목")
                .region(Region.HADONG)
                .build();
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.of(existing));
        stubDetailCalls();

        int collected = contentCollectService.collectRegion(REGION);

        assertThat(collected).isEqualTo(1);
        assertThat(existing.getTitle()).isEqualTo("화개장터");
        assertThat(existing.getDetail()).isNotNull();
        verify(travelContentRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("상세 보강이 실패하면 해당 콘텐츠를 건너뛰고 저장하지 않는다")
    void collectRegion_상세실패_건너뜀() {
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        // 상세 호출이 DB 조회보다 먼저이므로, 상세 실패 시 findById 는 호출되지 않는다.
        given(tourApiClient.getDetailCommon(CONTENT_ID)).willThrow(feignError());

        int collected = contentCollectService.collectRegion(REGION);

        assertThat(collected).isZero();
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("목록 응답이 오류 결과코드(HTTP 200)면 해당 타입을 건너뛴다")
    void collectRegion_오류코드_건너뜀() {
        given(tourApiClient.getAreaBasedList(eq("36"), eq("18"), anyString(), eq(1), eq(100)))
                .willReturn(errorListResponse());

        int collected = contentCollectService.collectRegion(REGION);

        assertThat(collected).isZero();
        verify(travelContentRepository, never()).save(any());
    }

    private TourApiListResponse errorListResponse() {
        return new TourApiListResponse(new TourApiListResponse.Response(
                new TourApiListResponse.Header("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"),
                new TourApiListResponse.Body(new TourApiListResponse.Items(List.of()), 0, 1, 0)));
    }
}
