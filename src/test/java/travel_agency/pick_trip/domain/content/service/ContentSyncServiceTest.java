package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiSyncResponse;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentSyncService")
class ContentSyncServiceTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private TravelContentRepository travelContentRepository;

    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private ContentSyncService contentSyncService;

    @BeforeEach
    void setUpTx() {
        // execute 는 콜백(변경분 반영)을 즉시 실행하도록 스텁한다.
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
    }

    private static final Region REGION = Region.HADONG; // areaCode=36, sigunguCode=18
    private static final String CONTENT_ID = "126508";

    private TravelContent activeContent() {
        return TravelContent.builder()
                .sourceContentId(CONTENT_ID)
                .title("화개장터")
                .region(Region.HADONG)
                .dataStatus(DataStatus.ACTIVE)
                .build();
    }

    private TourApiSyncResponse syncResponse(String showflag) {
        return new TourApiSyncResponse(new TourApiSyncResponse.Response(
                new TourApiSyncResponse.Body(new TourApiSyncResponse.Items(List.of(
                        new TourApiSyncResponse.Item(CONTENT_ID, "12", "화개장터", "20260601", showflag))),
                        100, 1, 1)));
    }

    private FeignException feignError() {
        return new FeignException(500, "tour-api 5xx") {};
    }

    @Test
    @DisplayName("showflag=0 이면 적재 콘텐츠를 INACTIVE 로 변경한다")
    void syncRegion_showflag0_INACTIVE() {
        TravelContent content = activeContent();
        given(tourApiClient.getAreaBasedSyncList("48", "850", null, null, 1, 100))
                .willReturn(syncResponse("0"));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.of(content));

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isEqualTo(1);
        assertThat(content.getDataStatus()).isEqualTo(DataStatus.INACTIVE);
        verify(travelContentRepository).save(content);
    }

    @Test
    @DisplayName("상태 변화가 없으면 저장하지 않는다")
    void syncRegion_상태동일_미변경() {
        TravelContent content = activeContent();
        given(tourApiClient.getAreaBasedSyncList("48", "850", null, null, 1, 100))
                .willReturn(syncResponse("1"));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.of(content));

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isZero();
        assertThat(content.getDataStatus()).isEqualTo(DataStatus.ACTIVE);
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("아직 적재되지 않은 콘텐츠는 건너뛴다")
    void syncRegion_미적재_건너뜀() {
        given(tourApiClient.getAreaBasedSyncList("48", "850", null, null, 1, 100))
                .willReturn(syncResponse("0"));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.empty());

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isZero();
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("동기화 조회가 실패하면 예외를 전파하지 않는다")
    void syncRegion_조회실패_미반영() {
        given(tourApiClient.getAreaBasedSyncList(eq("48"), eq("850"), isNull(), isNull(), eq(1), eq(100)))
                .willThrow(feignError());

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isZero();
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("오류 결과코드(HTTP 200) 응답이면 반영하지 않는다")
    void syncRegion_오류코드_미반영() {
        given(tourApiClient.getAreaBasedSyncList("48", "850", null, null, 1, 100))
                .willReturn(errorResponse());

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isZero();
        verify(travelContentRepository, never()).save(any());
    }

    private TourApiSyncResponse errorResponse() {
        return new TourApiSyncResponse(new TourApiSyncResponse.Response(
                new TourApiSyncResponse.Header("22", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"),
                new TourApiSyncResponse.Body(new TourApiSyncResponse.Items(List.of()), 0, 1, 0)));
    }
}
