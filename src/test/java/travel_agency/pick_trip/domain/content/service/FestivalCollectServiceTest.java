package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiFestivalResponse;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("FestivalCollectService")
class FestivalCollectServiceTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private TravelContentRepository travelContentRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private FestivalCollectService festivalCollectService;

    private static final Region REGION = Region.HADONG; // areaCode=36, sigunguCode=18
    private static final String EVENT_FROM = "20260701";
    private static final String FESTIVAL_ID = "2733967";

    @BeforeEach
    void setUp() {
        festivalCollectService = new FestivalCollectService(
                tourApiClient, travelContentRepository, new ContentCollectMapper(), transactionTemplate);
        // execute 는 콜백(축제 upsert)을 즉시 실행하도록 스텁한다.
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
    }

    private TourApiFestivalResponse festivalResponse(TourApiFestivalResponse.Item... items) {
        return new TourApiFestivalResponse(new TourApiFestivalResponse.Response(
                new TourApiFestivalResponse.Body(new TourApiFestivalResponse.Items(List.of(items)), 100, 1, items.length)));
    }

    private TourApiFestivalResponse.Item festivalItem() {
        return new TourApiFestivalResponse.Item(
                FESTIVAL_ID, "15", "하동 화개장터 벚꽃축제", "경남 하동군", "화개면", "055-000-1111",
                "127.7", "35.1", "festival.jpg", "20260705", "20260707");
    }

    private FeignException feignError() {
        return new FeignException(500, "tour-api 5xx") {};
    }

    @Test
    @DisplayName("신규 축제를 행사 기간과 함께 저장한다")
    void collectFestivals_신규축제_저장() {
        given(tourApiClient.searchFestival(EVENT_FROM, "48", "850", 1, 100))
                .willReturn(festivalResponse(festivalItem()));
        given(travelContentRepository.findById(FESTIVAL_ID)).willReturn(Optional.empty());

        int collected = festivalCollectService.collectFestivals(REGION, EVENT_FROM);

        assertThat(collected).isEqualTo(1);

        ArgumentCaptor<TravelContent> captor = ArgumentCaptor.forClass(TravelContent.class);
        verify(travelContentRepository, times(1)).save(captor.capture());

        TravelContent saved = captor.getValue();
        assertThat(saved.getSourceContentId()).isEqualTo(FESTIVAL_ID);
        assertThat(saved.getContentTypeId()).isEqualTo("15");
        assertThat(saved.getTitle()).isEqualTo("하동 화개장터 벚꽃축제");
        assertThat(saved.getRegion()).isEqualTo(Region.HADONG);
        assertThat(saved.getDetail()).isNotNull();
        assertThat(saved.getDetail().getEventStartDate()).isEqualTo("20260705");
        assertThat(saved.getDetail().getEventEndDate()).isEqualTo("20260707");
    }

    @Test
    @DisplayName("축제 조회가 실패하면 예외를 전파하지 않고 저장하지 않는다")
    void collectFestivals_호출실패_저장없음() {
        given(tourApiClient.searchFestival(EVENT_FROM, "48", "850", 1, 100))
                .willThrow(feignError());

        int collected = festivalCollectService.collectFestivals(REGION, EVENT_FROM);

        assertThat(collected).isZero();
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("축제 응답이 오류 결과코드(HTTP 200)면 저장하지 않는다")
    void collectFestivals_오류코드_저장없음() {
        given(tourApiClient.searchFestival(EVENT_FROM, "48", "850", 1, 100))
                .willReturn(errorResponse());

        int collected = festivalCollectService.collectFestivals(REGION, EVENT_FROM);

        assertThat(collected).isZero();
        verify(travelContentRepository, never()).save(any());
    }

    private TourApiFestivalResponse errorResponse() {
        return new TourApiFestivalResponse(new TourApiFestivalResponse.Response(
                new TourApiFestivalResponse.Header("22", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"),
                new TourApiFestivalResponse.Body(new TourApiFestivalResponse.Items(List.of()), 0, 1, 0)));
    }
}
