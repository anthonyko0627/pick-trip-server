package travel_agency.pick_trip.domain.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.basket.entity.Priority;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse.NearbyContentItem;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.repository.projection.RegionContentProjection;
import travel_agency.pick_trip.domain.content.entity.CongestionLevel;
import travel_agency.pick_trip.domain.content.service.CongestionService;
import travel_agency.pick_trip.domain.content.service.ContentService;
import travel_agency.pick_trip.domain.content.service.RoadMatrixResolver;
import travel_agency.pick_trip.domain.itinerary.config.ItineraryCostProperties;
import travel_agency.pick_trip.domain.itinerary.dto.request.GenerateItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.request.GenerateMode;
import travel_agency.pick_trip.domain.itinerary.dto.request.SaveItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryGenerateResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItinerarySummaryResponse;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryDay;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryItem;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.itinerary.scheduling.ElevationProfile;
import travel_agency.pick_trip.domain.itinerary.scheduling.ItineraryPlanner;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMatrix;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMode;
import travel_agency.pick_trip.domain.itinerary.scheduling.VariantMetrics;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.domain.share.entity.ShareToken;
import travel_agency.pick_trip.domain.share.repository.ShareTokenRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ItineraryException;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;
import travel_agency.pick_trip.infra.ai.AiItineraryClient;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryRequest;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult.AiDay;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult.AiItem;
import travel_agency.pick_trip.infra.ai.dto.AiPlace;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItineraryService")
class ItineraryServiceTest {

    @Mock private BasketRepository basketRepository;
    @Mock private ContentService contentService;
    @Mock private TravelContentRepository travelContentRepository;
    @Mock private AiItineraryClient aiItineraryClient;
    @Mock private ItineraryRepository itineraryRepository;
    @Mock private ShareTokenRepository shareTokenRepository;
    @Mock private CongestionService congestionService;
    @Mock private RoadMatrixResolver roadMatrixResolver;
    // 설정값이라 mock 이 아닌 실제 값으로 주입해야 교통비 계산을 검증할 수 있다(application.yaml 기본값과 동일).
    @Spy private ItineraryCostProperties costProperties = new ItineraryCostProperties(142, 1500);
    @Mock private ElevationResolver elevationResolver;
    @InjectMocks private ItineraryService itineraryService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ITINERARY_ID = UUID.randomUUID();

    // --- 테스트 헬퍼 ---

    private Basket basketWith(Region region, Integer duration, String... contentIds) {
        Basket basket = Basket.builder().userId(USER_ID).build();
        basket.updateConditions(region, LocalDate.of(2026, 7, 1), duration, Set.of(TravelCondition.WITH_CHILD));
        for (String contentId : contentIds) {
            basket.addItem(BasketItem.builder()
                    .contentId(contentId)
                    .title("title-" + contentId)
                    .contentTypeId("12")
                    .priority(Priority.MUST_VISIT)
                    .build());
        }
        return basket;
    }

    private ContentDetailResponse detail(String contentId) {
        return detailAt(contentId, 35.0, 127.0);
    }

    /** 좌표가 갈려야 이동수단별 소요 시간 차이가 드러나는 테스트에서 쓴다. */
    private ContentDetailResponse detailAt(String contentId, double latitude, double longitude) {
        return new ContentDetailResponse(
                contentId, "title-" + contentId, 12, "주소", "010", "home",
                latitude, longitude, "요약", "09:00-18:00", "월요일", "가능", "무료",
                "없음", "불가", "2시간", Boolean.FALSE, "TourAPI", List.of(),
                ContentCategory.ATTRACTION, false, "HADONG", null
        );
    }

    private AiItineraryResult twoPlaceResult() {
        return new AiItineraryResult(
                "하동 1박 2일 가족 여행",
                List.of(new AiDay(1, List.of(
                        new AiItem("c1", 1, "오전 운영시간에 맞춰 배치했습니다."),
                        new AiItem("c2", 2, "동선상 인접해 오후에 배치했습니다.")
                )))
        );
    }

    private Itinerary itineraryOwnedBy(UUID ownerId) {
        Itinerary itinerary = Itinerary.builder()
                .userId(ownerId)
                .title("기존 제목")
                .region(Region.HADONG)
                .travelDate(LocalDate.of(2026, 7, 1))
                .duration(2)
                .build();
        ItineraryDay day = ItineraryDay.builder().dayIndex(1).build();
        day.addItem(ItineraryItem.builder()
                .contentId("c1").title("title-c1").orderIndex(1).reason("기존 이유").pinned(false).build());
        itinerary.addDay(day);
        return itinerary;
    }

    private Itinerary mockSummaryItinerary(UUID itineraryId, String title, LocalDateTime lastModifiedAt) {
        Itinerary itinerary = mock(Itinerary.class);
        given(itinerary.getItineraryId()).willReturn(itineraryId);
        given(itinerary.getTitle()).willReturn(title);
        given(itinerary.getRegion()).willReturn(Region.HADONG);
        given(itinerary.getTravelDate()).willReturn(LocalDate.of(2026, 7, 1));
        given(itinerary.getDuration()).willReturn(2);
        given(itinerary.getLastModifiedAt()).willReturn(lastModifiedAt);
        return itinerary;
    }

    private SaveItineraryRequest saveRequest() {
        return new SaveItineraryRequest(
                "새 제목", Region.HADONG, LocalDate.of(2026, 7, 1), 2,
                List.of(new SaveItineraryRequest.DayRequest(1, List.of(
                        new SaveItineraryRequest.ItemRequest(
                                "c1", "title-c1", 1, "이유1", true,
                                LocalTime.of(9, 0), LocalTime.of(10, 30)),
                        new SaveItineraryRequest.ItemRequest(
                                "c2", "title-c2", 2, "이유2", false,
                                LocalTime.of(11, 0), LocalTime.of(12, 30))
                ), 25, new BigDecimal("12.30")))
        );
    }

    @Nested
    @DisplayName("generate - 입력 검증")
    class ValidateInput {

        @Test
        @DisplayName("바구니가 없으면 ITINERARY_INPUT_INSUFFICIENT 예외를 던진다")
        void noBasket_throws() {
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            ThrowingCallable action = () -> itineraryService.generate(USER_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
            verify(aiItineraryClient, never()).generate(any());
        }

        @Test
        @DisplayName("콘텐츠가 2개 미만이면 ITINERARY_INPUT_INSUFFICIENT 예외를 던진다")
        void lessThanTwoContents_throws() {
            Basket basket = basketWith(Region.HADONG, 2, "c1");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            ThrowingCallable action = () -> itineraryService.generate(USER_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
            verify(aiItineraryClient, never()).generate(any());
        }

        @Test
        @DisplayName("여행 기간(duration)이 없으면 ITINERARY_INPUT_INSUFFICIENT 예외를 던진다")
        void noDuration_throws() {
            Basket basket = basketWith(Region.HADONG, null, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            ThrowingCallable action = () -> itineraryService.generate(USER_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
            verify(aiItineraryClient, never()).generate(any());
        }

        @Test
        @DisplayName("시작 지점이 바구니에 없으면 ITINERARY_INPUT_INSUFFICIENT 예외를 던진다")
        void startContentIdOutsideBasket_throws() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            ThrowingCallable action = () -> itineraryService.generate(
                    USER_ID, new GenerateItineraryRequest(GenerateMode.STRICT, "x9"));

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_INPUT_INSUFFICIENT);
            verify(aiItineraryClient, never()).generate(any());
        }

        @Test
        @DisplayName("시작 지점이 바구니에 있으면 검증을 통과해 일정을 생성한다")
        void startContentIdInBasket_passes() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            ItineraryGenerateResponse response = itineraryService.generate(
                    USER_ID, new GenerateItineraryRequest(GenerateMode.STRICT, "c2"));

            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c2");
        }
    }

    @Nested
    @DisplayName("generate - 정상 흐름")
    class Generate {

        @Test
        @DisplayName("콘텐츠 상세를 보강해 AI를 호출하고, 장소명은 바구니 스냅샷에서 매핑한다")
        void enrichesDetailAndMapsResponse() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.title()).isEqualTo("하동 1박 2일 가족 여행");
            assertThat(response.region()).isEqualTo(Region.HADONG);
            assertThat(response.duration()).isEqualTo(2);
            assertThat(response.days()).hasSize(1);
            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
            assertThat(response.days().get(0).items().get(0).title()).isEqualTo("title-c1");
            assertThat(response.days().get(0).items().get(0).reason()).contains("운영시간");

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            AiItineraryRequest request = captor.getValue();
            assertThat(request.places()).hasSize(2);
            assertThat(request.places().get(0).latitude()).isEqualTo(35.0);
            assertThat(request.regionName()).isEqualTo("하동");
            assertThat(request.companions()).containsExactly("아이와 함께");
            assertThat(request.places().get(0).priority()).isEqualTo("꼭 가기");
        }

        @Test
        @DisplayName("AI 배치 이유에 새어 나온 contentId·enum 코드를 응답에서 제거한다")
        void sanitizesLeakedInternalValuesInReason() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(new AiItineraryResult(
                    "하동 1박 2일 가족 여행",
                    List.of(new AiDay(1, List.of(
                            new AiItem("c1", 1, "슬로시티(773075)와 가깝고 LESS_WALKING 조건이라 먼저 배치했습니다."),
                            new AiItem("c2", 2, "동선상 인접해 오후에 배치했습니다.")
                    )))
            ));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            String firstReason = response.days().get(0).items().get(0).reason();
            assertThat(firstReason)
                    .doesNotContain("773075")
                    .doesNotContain("LESS_WALKING")
                    .contains("슬로시티")
                    .contains("걷기 적게");
        }

        @Test
        @DisplayName("생성 결과의 각 장소에 방문 시각이 배정되고 첫 스톱은 09:00에 시작한다")
        void assignsVisitTimes() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            // then
            List<ItineraryGenerateResponse.Item> items = response.days().get(0).items();
            assertThat(items.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(items).allSatisfy(item -> {
                assertThat(item.startTime()).isNotNull();
                assertThat(item.endTime()).isNotNull();
            });
        }

        @Test
        @DisplayName("스케줄링이 실패해도 예외 없이 AI 순서 그대로 미리보기를 반환한다")
        void schedulingFails_fallsBackToAiOrder() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            try (MockedStatic<ItineraryPlanner> planner = mockStatic(ItineraryPlanner.class)) {
                planner.when(() -> ItineraryPlanner.plan(any(), any(), any(), any(), any(), any()))
                        .thenThrow(new IllegalStateException("스케줄링 붕괴"));

                // when
                ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

                // then
                assertThat(response.title()).isEqualTo("하동 1박 2일 가족 여행");
                assertThat(response.days().get(0).items())
                        .extracting(ItineraryGenerateResponse.Item::contentId)
                        .containsExactly("c1", "c2");
                assertThat(response.days().get(0).items().get(0).startTime()).isNull();
                assertThat(response.adjustments()).isEmpty();
            }
        }

        @Test
        @DisplayName("AI가 바구니에 없는 장소만 반환하면 해당 일차를 비운다")
        void unknownContentIds_areFilteredOut() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(new AiItineraryResult(
                    "지어낸 일정",
                    List.of(new AiDay(1, List.of(new AiItem("없는-장소", 1, "환각"))))
            ));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            // then
            assertThat(response.days()).hasSize(1);
            assertThat(response.days().get(0).items()).isEmpty();
        }

        @Test
        @DisplayName("콘텐츠 상세 조회가 실패해도 바구니 스냅샷만으로 AI를 호출한다")
        void detailFails_fallsBackToSnapshot() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willThrow(new RuntimeException("TourAPI 장애"));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.days().get(0).items()).hasSize(2);

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().places().get(0).latitude()).isNull();
            assertThat(captor.getValue().places().get(0).category()).isEqualTo("12");
        }

        @Test
        @DisplayName("AI 응답에 바구니에 없는 contentId가 섞이면 해당 항목을 제외한다")
        void unknownContentId_isFilteredOut() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(new AiItineraryResult(
                    "하동 1박 2일 가족 여행",
                    List.of(new AiDay(1, List.of(
                            new AiItem("c1", 1, "바구니에 있는 장소입니다."),
                            new AiItem("ghost-99", 2, "AI가 지어낸 장소입니다."),
                            new AiItem("c2", 3, "바구니에 있는 장소입니다.")
                    )))
            ));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
            // 스케줄러가 순서를 다시 정하므로 AI 원본 번호(1, 3)가 아니라 1부터 다시 매겨진다.
            // 걸러낸 자리에 구멍이 남지 않고, 재정렬이 일어나도 번호가 실제 방문 순서와 일치한다.
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::order)
                    .containsExactly(1, 2);
        }

        @Test
        @DisplayName("일차의 모든 항목이 바구니에 없으면 빈 일차로 남긴다 (구조는 그대로)")
        void allItemsUnknown_keepsEmptyDay() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(new AiItineraryResult(
                    "하동 1박 2일 가족 여행",
                    List.of(
                            new AiDay(1, List.of(new AiItem("c1", 1, "바구니에 있는 장소입니다."))),
                            new AiDay(2, List.of(new AiItem("ghost-1", 1, "지어낸 장소"), new AiItem("ghost-2", 2, "지어낸 장소")))
                    )
            ));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.days()).hasSize(2);
            assertThat(response.days().get(0).items()).extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1");
            assertThat(response.days().get(1).items()).isEmpty();
        }

        @Test
        @DisplayName("AI 제공자 호출이 실패하면 예외가 그대로 전파된다")
        void aiFails_propagates() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any()))
                    .willThrow(new ItineraryException(ErrorCode.ITINERARY_PROVIDER_FAILED));

            ThrowingCallable action = () -> itineraryService.generate(USER_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_PROVIDER_FAILED);
        }
    }

    @Nested
    @DisplayName("generate - 생성 모드")
    class Modes {

        private void givenBasketAndDetails(Basket basket) {
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
        }

        private AiItineraryResult resultWithExtra(String extraContentId, String extraReason) {
            return new AiItineraryResult(
                    "하동 1박 2일 가족 여행",
                    List.of(new AiDay(1, List.of(
                            new AiItem("c1", 1, "바구니에 있는 장소입니다."),
                            new AiItem(extraContentId, 2, extraReason),
                            new AiItem("c2", 3, "바구니에 있는 장소입니다.")
                    )))
            );
        }

        @Test
        @DisplayName("모드를 지정하지 않으면 STRICT 로 동작해 바구니 밖 장소를 모두 제거한다")
        void defaultRequest_behavesAsStrict() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("x9", "지역 콘텐츠입니다."));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, GenerateItineraryRequest.defaults());

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::addedByAi)
                    .containsOnly(false);
            verify(travelContentRepository, never()).findIdsByRegion(any(), any(), any());
            verify(travelContentRepository, never()).findRegionCandidates(any(), any(), any(), any());

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().extraCandidates()).isEmpty();
        }

        @Test
        @DisplayName("AUGMENT 면 같은 지역에 적재된 추가 장소를 유지하고 addedByAi 를 true 로 표시한다")
        void augment_keepsRegionContentAndFlagsIt() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            // 후보로 제시한 장소를 AI 가 실제로 골라 오는 AUGMENT 정상 흐름
            given(travelContentRepository.findRegionCandidates(
                    eq(Region.HADONG), eq(DataStatus.ACTIVE), any(), any()))
                    .willReturn(List.of(new RegionContentProjection("x9", "최참판댁", "12")));
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("x9", "빈 시간을 채우려고 넣었습니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of("x9"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            List<ItineraryGenerateResponse.Item> items = response.days().get(0).items();
            // 방문 순서는 서버가 좌표로 다시 정하므로(테스트 상세는 좌표가 모두 같아 "꼭 가기"가 먼저 잡힌다)
            // 여기서는 AUGMENT 로 추가된 장소가 일정에 남았는지만 본다.
            assertThat(items)
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactlyInAnyOrder("c1", "x9", "c2");
            assertThat(items)
                    .filteredOn(item -> item.contentId().equals("x9"))
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.addedByAi()).isTrue();
                        // 바구니 스냅샷에 없으므로 표시명은 스케줄러가 들고 있던 콘텐츠 상세 값으로 폴백한다.
                        assertThat(item.title()).isEqualTo("title-x9");
                    });
            assertThat(items)
                    .filteredOn(item -> !item.contentId().equals("x9"))
                    .extracting(ItineraryGenerateResponse.Item::addedByAi)
                    .containsOnly(false);

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().extraCandidates())
                    .extracting(AiPlace::contentId)
                    .containsExactly("x9");
        }

        @Test
        @DisplayName("AUGMENT 면 같은 지역 후보를 AI 프롬프트 입력으로 실어 보낸다 (바구니 항목은 제외)")
        void augment_sendsRegionCandidatesToAi() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
            given(travelContentRepository.findRegionCandidates(
                    eq(Region.HADONG), eq(DataStatus.ACTIVE), any(), any()))
                    .willReturn(List.of(
                            new RegionContentProjection("x9", "최참판댁", "12"),
                            new RegionContentProjection("x10", "화개장터", "14")));

            // when
            itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().extraCandidates())
                    .extracting(AiPlace::contentId, AiPlace::title, AiPlace::category)
                    .containsExactly(tuple("x9", "최참판댁", "12"), tuple("x10", "화개장터", "14"));
            // 후보에 상세는 채우지 않는다 (추가 API 호출 없이 repository 한 번으로 끝낸다).
            assertThat(captor.getValue().extraCandidates())
                    .allSatisfy(candidate -> assertThat(candidate.latitude()).isNull());

            ArgumentCaptor<Collection<String>> excludedCaptor = ArgumentCaptor.forClass(Collection.class);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(travelContentRepository).findRegionCandidates(
                    eq(Region.HADONG), eq(DataStatus.ACTIVE), excludedCaptor.capture(), pageableCaptor.capture());
            assertThat(excludedCaptor.getValue()).containsExactlyInAnyOrder("c1", "c2");
            // 프롬프트 토큰 비용 때문에 후보 수는 상한으로 잘라 조회한다.
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(80);
        }

        @Test
        @DisplayName("AUGMENT 인데 지역 후보가 없으면 후보 없이 정상 생성한다")
        void augment_withoutCandidates_stillGenerates() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
            given(travelContentRepository.findRegionCandidates(any(), any(), any(), any()))
                    .willReturn(List.of());

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
        }

        @Test
        @DisplayName("AUGMENT 후보 조회가 실패해도 예외 없이 후보 없이 생성한다")
        void augment_candidateQueryFails_generatesWithoutCandidates() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
            given(travelContentRepository.findRegionCandidates(any(), any(), any(), any()))
                    .willThrow(new RuntimeException("DB 장애"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");

            ArgumentCaptor<AiItineraryRequest> captor = ArgumentCaptor.forClass(AiItineraryRequest.class);
            verify(aiItineraryClient).generate(captor.capture());
            assertThat(captor.getValue().extraCandidates()).isEmpty();
        }

        @Test
        @DisplayName("AUGMENT 로 추가된 장소도 스케줄링으로 방문 시각을 배정받는다")
        void augment_extraPlaceGetsVisitTimes() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("x9", "빈 시간을 채우려고 넣었습니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of("x9"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items()).allSatisfy(item -> {
                assertThat(item.startTime()).isNotNull();
                assertThat(item.endTime()).isNotNull();
            });
        }

        @Test
        @DisplayName("AUGMENT 로 추가된 장소의 배치 이유도 내부 값 정제를 거친다")
        void augment_extraPlaceReasonIsSanitized() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(
                    resultWithExtra("x9", "슬로시티(773075)와 가깝고 LESS_WALKING 조건이라 추가했습니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of("x9"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            String extraReason = response.days().get(0).items().stream()
                    .filter(item -> item.contentId().equals("x9"))
                    .findFirst()
                    .orElseThrow()
                    .reason();
            assertThat(extraReason)
                    .doesNotContain("773075")
                    .doesNotContain("LESS_WALKING")
                    .contains("슬로시티")
                    .contains("걷기 적게");
        }

        @Test
        @DisplayName("AUGMENT 여도 DB 에 없는 contentId 는 제거한다")
        void augment_removesUnknownContentId() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("ghost-99", "AI가 지어낸 장소입니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of());

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
        }

        @Test
        @DisplayName("AUGMENT 여도 다른 지역 콘텐츠는 제거한다 (조회를 바구니 지역으로 한정한다)")
        void augment_removesOtherRegionContent() {
            // given: 영주 콘텐츠라 하동 지역 조회 결과에 포함되지 않는다
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            givenBasketAndDetails(basket);
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("yeongju-1", "영주 콘텐츠입니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of());

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
            verify(travelContentRepository).findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE));
        }

        @Test
        @DisplayName("AUGMENT 에서 추가 장소의 상세 조회가 실패하면 그 장소만 제외하고 일정은 생성한다")
        void augment_detailFailure_dropsOnlyThatPlace() {
            // given
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString())).willAnswer(invocation -> {
                if ("x9".equals(invocation.getArgument(0))) {
                    throw new RuntimeException("TourAPI 장애");
                }
                return detail(invocation.getArgument(0));
            });
            given(aiItineraryClient.generate(any())).willReturn(resultWithExtra("x9", "빈 시간을 채우려고 넣었습니다."));
            given(travelContentRepository.findIdsByRegion(any(), eq(Region.HADONG), eq(DataStatus.ACTIVE)))
                    .willReturn(List.of("x9"));

            // when
            ItineraryGenerateResponse response =
                    itineraryService.generate(USER_ID, new GenerateItineraryRequest(GenerateMode.AUGMENT));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2");
        }
    }

    @Nested
    @DisplayName("generate - 이동수단별 일정안")
    class TravelModes {

        /** c1·c2 를 위도 0.1도(약 11km) 떨어뜨려 이동수단별 소요 시간 차이가 드러나게 한다. */
        private Basket twoPlacesApart() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString())).willAnswer(invocation -> {
                String contentId = invocation.getArgument(0);
                return detailAt(contentId, "c1".equals(contentId) ? 35.0 : 35.1, 127.0);
            });
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
            return basket;
        }

        @Test
        @DisplayName("이동수단을 지정하지 않으면 자동차 단일안이고 최상위 필드는 첫 안의 복제다")
        void defaultsToSingleCarVariant() {
            // given
            twoPlacesApart();

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            // then
            assertThat(response.variants()).hasSize(1);
            ItineraryGenerateResponse.Variant primary = response.variants().get(0);
            assertThat(primary.travelMode()).isEqualTo(TravelMode.CAR);
            assertThat(primary.label()).isEqualTo("자동차 힐링 루트");
            assertThat(response.title()).isEqualTo(primary.title());
            assertThat(response.days()).isEqualTo(primary.days());
            assertThat(response.adjustments()).isEqualTo(primary.adjustments());
        }

        @Test
        @DisplayName("이동수단을 두 개 지정하면 안이 두 개 나오고 대중교통 안의 이동시간이 더 길다")
        void buildsOneVariantPerTravelMode() {
            // given
            twoPlacesApart();
            GenerateItineraryRequest request = new GenerateItineraryRequest(
                    null, null, List.of(TravelMode.CAR, TravelMode.TRANSIT));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, request);

            // then
            assertThat(response.variants()).extracting(ItineraryGenerateResponse.Variant::travelMode)
                    .containsExactly(TravelMode.CAR, TravelMode.TRANSIT);
            assertThat(response.variants()).extracting(ItineraryGenerateResponse.Variant::label)
                    .containsExactly("자동차 힐링 루트", "뚜벅이 가성비 루트");
            int carMinutes = response.variants().get(0).days().get(0).totalTravelMinutes();
            int transitMinutes = response.variants().get(1).days().get(0).totalTravelMinutes();
            assertThat(carMinutes).isLessThan(transitMinutes);
            // AI 는 안 개수와 무관하게 한 번만 호출한다.
            verify(aiItineraryClient).generate(any());
        }

        @Test
        @DisplayName("안마다 비교 지표를 담고 이동수단에 따라 도보 시간·교통비 계산이 갈린다")
        void buildsMetricsPerVariant() {
            // given - 약 11.12km 떨어진 두 곳
            twoPlacesApart();
            GenerateItineraryRequest request = new GenerateItineraryRequest(
                    null, null, List.of(TravelMode.CAR, TravelMode.TRANSIT));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, request);

            // then
            VariantMetrics car = response.variants().get(0).metrics();
            assertThat(car.placeCount()).isEqualTo(2);
            assertThat(car.totalTravelMinutes()).isEqualTo(25);
            // 자동차는 주차 후 도보를 모델에 두지 않아 0 이며, 11.12km * 142원/km = 1,579원
            assertThat(car.totalWalkingMinutes()).isZero();
            assertThat(car.totalTransitCost()).isEqualTo(1579);

            VariantMetrics transit = response.variants().get(1).metrics();
            assertThat(transit.placeCount()).isEqualTo(2);
            // 도보 한계(2km)를 넘는 구간이라 전부 승차이며 기본요금 한 번이 붙는다.
            assertThat(transit.totalWalkingMinutes()).isZero();
            assertThat(transit.totalTransitCost()).isEqualTo(1500);
            assertThat(car.unavailableReasons()).isEmpty();
            assertThat(transit.unavailableReasons()).isEmpty();
        }

        @Test
        @DisplayName("이동수단이 달라도 지표 JSON 키 집합이 같아 스플릿 뷰가 정렬된다")
        void keepsSameMetricKeysAcrossVariants() throws Exception {
            // given
            twoPlacesApart();
            GenerateItineraryRequest request = new GenerateItineraryRequest(
                    null, null, List.of(TravelMode.CAR, TravelMode.TRANSIT));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, request);

            // then - 산출 불가 지표도 키를 빼지 않고 null 로 내려야 열이 어긋나지 않는다.
            List<Set<String>> keySets = response.variants().stream()
                    .map(variant -> metricKeys(variant.metrics()))
                    .toList();
            assertThat(keySets).hasSize(2);
            assertThat(keySets.get(0)).isEqualTo(keySets.get(1))
                    .containsExactlyInAnyOrder("totalTravelMinutes", "totalWalkingMinutes",
                            "totalTransitCost", "placeCount", "unavailableReasons");
        }

        @SuppressWarnings("unchecked")
        private Set<String> metricKeys(VariantMetrics metrics) {
            return ((Map<String, Object>) new ObjectMapper().convertValue(metrics, Map.class)).keySet();
        }

        @Test
        @DisplayName("대중교통 안만 요청하면 자동차 도로 거리를 조회하지 않는다")
        void doesNotResolveRoadMatrixForTransitOnly() {
            // given
            twoPlacesApart();
            GenerateItineraryRequest request =
                    new GenerateItineraryRequest(null, null, List.of(TravelMode.TRANSIT));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, request);

            // then
            assertThat(response.variants()).hasSize(1);
            verify(roadMatrixResolver, never()).resolve(any());
        }

        @Test
        @DisplayName("자동차 안은 실제 도로 거리 행렬을 이동량 계산에 사용한다")
        void usesRoadMatrixForCarVariant() {
            // given - 직선 약 11km 구간을 도로 20km·40분으로 실측한 행렬
            twoPlacesApart();
            given(roadMatrixResolver.resolve(any())).willReturn(new TravelMatrix(
                    Map.of(
                            TravelMatrix.key("c1", "c2"), new TravelMatrix.Leg(20.0, 40),
                            TravelMatrix.key("c2", "c1"), new TravelMatrix.Leg(20.0, 40)),
                    35.0));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            // then
            ItineraryGenerateResponse.Day day = response.days().get(0);
            assertThat(day.totalTravelMinutes()).isEqualTo(40);
            assertThat(day.totalTravelKm()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("도로 거리 조회가 전부 실패해 빈 행렬이어도 직선거리로 정상 생성한다")
        void fallsBackToStraightDistanceWhenMatrixIsEmpty() {
            // given
            twoPlacesApart();
            given(roadMatrixResolver.resolve(any())).willReturn(TravelMatrix.empty());

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            // then
            // 직선 약 11.12km * 1.3 / 35km/h = 25분
            assertThat(response.days().get(0).totalTravelMinutes()).isEqualTo(25);
        }

        @Test
        @DisplayName("도로 거리 조회가 예외를 던져도 일정 생성은 성공한다")
        void survivesRoadMatrixFailure() {
            // given
            twoPlacesApart();
            given(roadMatrixResolver.resolve(any())).willThrow(new IllegalStateException("kakao down"));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            // then
            assertThat(response.variants()).hasSize(1);
            assertThat(response.days().get(0).items()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("generate - 체력 안배 휴식 스톱 자동 삽입")
    class RestStops {

        /** c1~c4 를 경도 0.005도(약 0.46km) 간격으로 늘어놓아 전 구간이 도보 경계 안에 들어오게 한다. */
        private static final double LONGITUDE_STEP = 0.005;

        private double longitudeOf(String contentId) {
            return 127.0 + (Integer.parseInt(contentId.substring(1)) - 1) * LONGITUDE_STEP;
        }

        /** 운영시간을 비워 둔 상세. 휴식 삽입만 보려는 테스트에서 운영시간 경고가 끼어들지 않게 한다. */
        private ContentDetailResponse openAllDay(String contentId) {
            return new ContentDetailResponse(
                    contentId, "title-" + contentId, 12, "주소", "010", "home",
                    35.0, longitudeOf(contentId), "요약", null, null, "가능", "무료",
                    "없음", "불가", "2시간", Boolean.FALSE, "TourAPI", List.of(),
                    ContentCategory.ATTRACTION, false, "HADONG", null);
        }

        private AiItineraryResult fourPlaceResult() {
            return new AiItineraryResult("하동 도보 여행", List.of(new AiDay(1, List.of(
                    new AiItem("c1", 1, "출발 지점입니다."),
                    new AiItem("c2", 2, "동선상 인접합니다."),
                    new AiItem("c3", 3, "동선상 인접합니다."),
                    new AiItem("c4", 4, "동선상 인접합니다.")
            ))));
        }

        /** c1→c2→c3 구간이 150m 씩 오르막이라 c3 에 닿으면 누적 상승고도가 임계(200m)를 넘는다. */
        private void givenUphillWalkingCourse() {
            Basket basket = basketWith(Region.HADONG, 1, "c1", "c2", "c3", "c4");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> openAllDay(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(fourPlaceResult());
            given(elevationResolver.resolve(any())).willReturn(new ElevationProfile(Map.of(
                    ElevationProfile.key(35.0, longitudeOf("c1")), 0.0,
                    ElevationProfile.key(35.0, longitudeOf("c2")), 150.0,
                    ElevationProfile.key(35.0, longitudeOf("c3")), 300.0,
                    ElevationProfile.key(35.0, longitudeOf("c4")), 300.0)));
        }

        private NearbyContentItem cafe(String contentId, double longitude) {
            return new NearbyContentItem(contentId, "카페-" + contentId, "39", "주소", null,
                    35.0, longitude, ContentCategory.FOOD, "요약", "HADONG", 0.1);
        }

        private NearbyContentResponse nearby(NearbyContentItem... items) {
            return new NearbyContentResponse(
                    "c3", 2.0, NearbyContentResponse.NearbySource.LOCAL, List.of(items));
        }

        private GenerateItineraryRequest transitRequest() {
            return new GenerateItineraryRequest(null, "c1", List.of(TravelMode.TRANSIT));
        }

        @Test
        @DisplayName("누적 상승고도가 임계를 넘으면 근처 카페를 휴식 스톱으로 끼워 넣는다")
        void insertsRestStopWhenClimbAccumulates() {
            // given
            givenUphillWalkingCourse();
            given(contentService.getNearbyContents(eq("c3"), anyDouble(), anyInt()))
                    .willReturn(nearby(cafe("cafe1", 127.011)));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, transitRequest());

            // then
            List<ItineraryGenerateResponse.Item> items = response.days().get(0).items();
            assertThat(items).extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2", "c3", "cafe1", "c4");
            assertThat(items).extracting(ItineraryGenerateResponse.Item::order)
                    .containsExactly(1, 2, 3, 4, 5);

            ItineraryGenerateResponse.Item rest = items.get(3);
            assertThat(rest.addedForRest()).isTrue();
            // 자동 삽입 휴식은 AI 추가 제안과 구분돼야 한다.
            assertThat(rest.addedByAi()).isFalse();
            assertThat(rest.title()).isEqualTo("카페-cafe1");
            assertThat(rest.reason()).contains("오르막");
            assertThat(rest.startTime()).isNotNull();
            assertThat(rest.endTime()).isEqualTo(rest.startTime().plusMinutes(30));
            assertThat(items).filteredOn(item -> !item.contentId().equals("cafe1"))
                    .allSatisfy(item -> assertThat(item.addedForRest()).isFalse());
        }

        @Test
        @DisplayName("자동 삽입된 휴식 스톱이 비교 지표의 장소 수·도보 시간에 반영된다")
        void restStopIsCountedInMetrics() {
            // given - 첫 호출은 후보 없음(휴식 미삽입) 기준선, 두 번째 호출에서 카페를 준다.
            givenUphillWalkingCourse();
            given(contentService.getNearbyContents(eq("c3"), anyDouble(), anyInt()))
                    .willReturn(nearby(), nearby(cafe("cafe1", 127.011)));
            VariantMetrics withoutRest = itineraryService
                    .generate(USER_ID, transitRequest())
                    .variants().get(0).metrics();

            // when
            VariantMetrics withRest = itineraryService
                    .generate(USER_ID, transitRequest())
                    .variants().get(0).metrics();

            // then - 휴식 스톱을 되짚지 못하면 그 스톱을 낀 구간이 통째로 지표에서 빠진다.
            assertThat(withRest.placeCount()).isEqualTo(withoutRest.placeCount() + 1);
            // 전 구간이 도보 경계 안이라 도보 시간 합은 총 이동 시간과 같아야 한다.
            // 휴식 스톱 구간이 빠지면 이 등식이 깨진다.
            assertThat(withRest.totalWalkingMinutes()).isEqualTo(withRest.totalTravelMinutes());
            // 스톱이 하나 늘어 구간이 쪼개지므로 도보 시간은 줄어들 수 없다.
            assertThat(withRest.totalWalkingMinutes())
                    .isGreaterThanOrEqualTo(withoutRest.totalWalkingMinutes());
            assertThat(withRest.totalTransitCost()).isNotNull();
            assertThat(withRest.unavailableReasons()).isEmpty();
        }

        @Test
        @DisplayName("휴식 스톱을 끼워 넣으면 뒤따르는 스톱의 방문 시각이 밀린다")
        void shiftsFollowingVisitTimes() {
            // given - 첫 호출은 후보 없음(휴식 미삽입) 기준선, 두 번째 호출에서 카페를 준다.
            givenUphillWalkingCourse();
            given(contentService.getNearbyContents(eq("c3"), anyDouble(), anyInt()))
                    .willReturn(nearby(), nearby(cafe("cafe1", 127.011)));
            LocalTime lastStartWithoutRest = itineraryService
                    .generate(USER_ID, transitRequest())
                    .days().get(0).items().get(3).startTime();

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, transitRequest());

            // then
            List<ItineraryGenerateResponse.Item> items = response.days().get(0).items();
            assertThat(items.get(2).contentId()).isEqualTo("c3");
            assertThat(items.get(4).contentId()).isEqualTo("c4");
            assertThat(items.get(4).startTime()).isAfter(lastStartWithoutRest.plusMinutes(29));
        }

        @Test
        @DisplayName("근처에 카페가 없으면 휴식 스톱 없이 일정을 그대로 생성한다")
        void noCafeNearby_keepsItineraryAsIs() {
            // given
            givenUphillWalkingCourse();
            given(contentService.getNearbyContents(eq("c3"), anyDouble(), anyInt()))
                    .willReturn(nearby());

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, transitRequest());

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2", "c3", "c4");
        }

        @Test
        @DisplayName("근처 조회가 예외를 던져도 휴식 스톱 없이 일정 생성은 성공한다")
        void nearbyLookupFailure_keepsItineraryAsIs() {
            // given
            givenUphillWalkingCourse();
            given(contentService.getNearbyContents(eq("c3"), anyDouble(), anyInt()))
                    .willThrow(new IllegalStateException("TourAPI 장애"));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, transitRequest());

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2", "c3", "c4");
        }

        @Test
        @DisplayName("이미 그날 일정에 있는 장소는 휴식 스톱으로 다시 넣지 않는다")
        void skipsPlaceAlreadyInItinerary() {
            // given - 근처 1순위가 이미 일정에 있는 c2 다.
            givenUphillWalkingCourse();
            given(contentService.getNearbyContents(eq("c3"), anyDouble(), anyInt()))
                    .willReturn(nearby(cafe("c2", longitudeOf("c2")), cafe("cafe1", 127.011)));

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, transitRequest());

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2", "c3", "cafe1", "c4");
        }

        @Test
        @DisplayName("자동차 안에는 휴식 스톱을 넣지 않는다")
        void carVariantHasNoRestStop() {
            // given
            givenUphillWalkingCourse();

            // when
            ItineraryGenerateResponse response = itineraryService.generate(
                    USER_ID, new GenerateItineraryRequest(null, "c1", List.of(TravelMode.CAR)));

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2", "c3", "c4");
            verify(contentService, never()).getNearbyContents(anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("고도 조회는 일정안 수와 무관하게 한 번만 한다")
        void resolvesElevationOnceRegardlessOfVariantCount() {
            // given
            givenUphillWalkingCourse();
            given(contentService.getNearbyContents(eq("c3"), anyDouble(), anyInt()))
                    .willReturn(nearby());

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID,
                    new GenerateItineraryRequest(null, "c1", List.of(TravelMode.CAR, TravelMode.TRANSIT)));

            // then
            assertThat(response.variants()).hasSize(2);
            verify(elevationResolver, times(1)).resolve(any());
        }

        @Test
        @DisplayName("고도를 하나도 모르면 휴식 스톱 없이 기존과 같은 일정이 나온다")
        void unknownElevations_keepsPreviousItinerary() {
            // given
            Basket basket = basketWith(Region.HADONG, 1, "c1", "c2", "c3", "c4");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> openAllDay(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(fourPlaceResult());
            given(elevationResolver.resolve(any())).willReturn(ElevationProfile.unknown());

            // when
            ItineraryGenerateResponse response = itineraryService.generate(USER_ID, transitRequest());

            // then
            assertThat(response.days().get(0).items())
                    .extracting(ItineraryGenerateResponse.Item::contentId)
                    .containsExactly("c1", "c2", "c3", "c4");
            verify(contentService, never()).getNearbyContents(anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("자동 삽입된 휴식 스톱도 저장·수정 흐름을 그대로 통과한다")
        void restStopPassesSaveAndModifyFlow() {
            // given
            givenUphillWalkingCourse();
            given(contentService.getNearbyContents(eq("c3"), anyDouble(), anyInt()))
                    .willReturn(nearby(cafe("cafe1", 127.011)));
            given(itineraryRepository.save(any(Itinerary.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            ItineraryGenerateResponse generated = itineraryService.generate(USER_ID, transitRequest());

            // when
            ItineraryResponse saved = itineraryService.save(USER_ID, toSaveRequest(generated));

            // then
            assertThat(saved.days().get(0).items())
                    .extracting(ItineraryResponse.Item::contentId)
                    .containsExactly("c1", "c2", "c3", "cafe1", "c4");
            ItineraryResponse.Item rest = saved.days().get(0).items().get(3);
            assertThat(rest.title()).isEqualTo("카페-cafe1");
            assertThat(rest.reason()).contains("오르막");
            assertThat(rest.startTime()).isNotNull();
            assertThat(rest.endTime()).isNotNull();

            // and - 수정 흐름도 같은 요청 형식을 그대로 받는다.
            Itinerary owned = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(owned));
            ItineraryResponse modified =
                    itineraryService.modify(USER_ID, ITINERARY_ID, toSaveRequest(generated));
            assertThat(modified.days().get(0).items())
                    .extracting(ItineraryResponse.Item::contentId)
                    .containsExactly("c1", "c2", "c3", "cafe1", "c4");
        }

        /** 미리보기 응답을 클라이언트가 그대로 저장 요청으로 되돌려 보내는 흐름을 재현한다. */
        private SaveItineraryRequest toSaveRequest(ItineraryGenerateResponse generated) {
            return new SaveItineraryRequest(
                    generated.title(), generated.region(), generated.travelDate(), generated.duration(),
                    generated.days().stream()
                            .map(day -> new SaveItineraryRequest.DayRequest(
                                    day.dayIndex(),
                                    day.items().stream()
                                            .map(item -> new SaveItineraryRequest.ItemRequest(
                                                    item.contentId(), item.title(), item.order(), item.reason(),
                                                    false, item.startTime(), item.endTime()))
                                            .toList(),
                                    day.totalTravelMinutes(),
                                    BigDecimal.valueOf(day.totalTravelKm())))
                            .toList());
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("요청을 일정 엔티티로 변환해 저장하고 응답으로 매핑한다")
        void savesItinerary() {
            given(itineraryRepository.save(any(Itinerary.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            ItineraryResponse response = itineraryService.save(USER_ID, saveRequest());

            assertThat(response.title()).isEqualTo("새 제목");
            assertThat(response.region()).isEqualTo(Region.HADONG);
            assertThat(response.days()).hasSize(1);
            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
            assertThat(response.days().get(0).items().get(0).pinned()).isTrue();
            assertThat(response.days().get(0).items().get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(response.days().get(0).totalTravelMinutes()).isEqualTo(25);
            verify(itineraryRepository).save(any(Itinerary.class));
        }
    }

    @Nested
    @DisplayName("getItinerary")
    class GetItinerary {

        @Test
        @DisplayName("본인 소유 일정이면 상세를 반환한다")
        void ownedItinerary_returnsResponse() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(USER_ID)));

            ItineraryResponse response = itineraryService.getItinerary(USER_ID, ITINERARY_ID);

            assertThat(response.title()).isEqualTo("기존 제목");
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
        }

        @Test
        @DisplayName("일정이 없으면 ITINERARY_NOT_FOUND 예외를 던진다")
        void notFound_throws() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.empty());

            ThrowingCallable action = () -> itineraryService.getItinerary(USER_ID, ITINERARY_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        }

        @Test
        @DisplayName("타인 소유 일정이면 존재를 숨기고 ITINERARY_NOT_FOUND 예외를 던진다")
        void notOwned_throws() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(UUID.randomUUID())));

            ThrowingCallable action = () -> itineraryService.getItinerary(USER_ID, ITINERARY_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("modify")
    class Modify {

        @Test
        @DisplayName("본인 일정의 일차·항목 구성을 통째로 교체한다")
        void replacesStructure() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));

            ItineraryResponse response = itineraryService.modify(USER_ID, ITINERARY_ID, saveRequest());

            assertThat(response.title()).isEqualTo("새 제목");
            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.days().get(0).items().get(0).pinned()).isTrue();
            assertThat(itinerary.getDays().get(0).getItems()).hasSize(2);
        }

        @Test
        @DisplayName("타인 소유 일정이면 ITINERARY_NOT_FOUND 예외를 던진다")
        void notOwned_throws() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(UUID.randomUUID())));

            ThrowingCallable action = () -> itineraryService.modify(USER_ID, ITINERARY_ID, saveRequest());

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("regenerate")
    class Regenerate {

        @Test
        @DisplayName("바구니 기준으로 다시 생성해 기존 일정을 덮어쓴다")
        void overwritesFromBasket() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));
            given(basketRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(basketWith(Region.HADONG, 2, "c1", "c2")));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());

            ItineraryResponse response = itineraryService.regenerate(USER_ID, ITINERARY_ID);

            assertThat(response.title()).isEqualTo("하동 1박 2일 가족 여행");
            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.days().get(0).items().get(1).contentId()).isEqualTo("c2");
        }
    }

    @Nested
    @DisplayName("getMyItineraries")
    class GetMyItineraries {

        @Test
        @DisplayName("로그인 사용자의 일정 목록을 최근 수정순 요약으로 반환한다")
        void returnsSummariesInRepositoryOrder() {
            UUID recentId = UUID.randomUUID();
            UUID olderId = UUID.randomUUID();
            Itinerary recent = mockSummaryItinerary(recentId, "최근 일정", LocalDateTime.of(2026, 7, 10, 0, 0));
            Itinerary older = mockSummaryItinerary(olderId, "이전 일정", LocalDateTime.of(2026, 7, 1, 0, 0));
            given(itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(USER_ID))
                    .willReturn(List.of(recent, older));

            List<ItinerarySummaryResponse> responses = itineraryService.getMyItineraries(USER_ID);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).itineraryId()).isEqualTo(recentId);
            assertThat(responses.get(0).title()).isEqualTo("최근 일정");
            assertThat(responses.get(0).region()).isEqualTo(Region.HADONG);
            assertThat(responses.get(0).travelDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(responses.get(0).duration()).isEqualTo(2);
            assertThat(responses.get(0).lastModifiedAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 0, 0));
            assertThat(responses.get(1).itineraryId()).isEqualTo(olderId);
        }

        @Test
        @DisplayName("저장된 일정이 없으면 빈 목록을 반환한다")
        void noItineraries_returnsEmptyList() {
            given(itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(USER_ID)).willReturn(List.of());

            List<ItinerarySummaryResponse> responses = itineraryService.getMyItineraries(USER_ID);

            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("본인 일정을 삭제하면 itineraryRepository.delete 가 호출된다")
        void deletesOwnedItinerary() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));
            given(shareTokenRepository.findByItineraryIdAndActiveTrue(ITINERARY_ID)).willReturn(Optional.empty());

            itineraryService.delete(USER_ID, ITINERARY_ID);

            verify(itineraryRepository).delete(itinerary);
        }

        @Test
        @DisplayName("타인 일정 삭제 요청은 ITINERARY_NOT_FOUND 예외를 던지고 delete 가 호출되지 않는다")
        void notOwned_throwsAndDoesNotDelete() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(UUID.randomUUID())));

            ThrowingCallable action = () -> itineraryService.delete(USER_ID, ITINERARY_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
            verify(itineraryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("일정 삭제 시 활성 공유 토큰이 비활성화된다")
        void deactivatesActiveShareToken() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            ShareToken shareToken = ShareToken.builder().itineraryId(ITINERARY_ID).token("share-token").build();
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));
            given(shareTokenRepository.findByItineraryIdAndActiveTrue(ITINERARY_ID))
                    .willReturn(Optional.of(shareToken));

            itineraryService.delete(USER_ID, ITINERARY_ID);

            assertThat(shareToken.isActive()).isFalse();
            verify(itineraryRepository).delete(itinerary);
        }

        @Test
        @DisplayName("활성 공유 토큰이 없어도 삭제가 정상 동작한다")
        void noActiveShareToken_stillDeletes() {
            Itinerary itinerary = itineraryOwnedBy(USER_ID);
            given(itineraryRepository.findWithDaysById(ITINERARY_ID)).willReturn(Optional.of(itinerary));
            given(shareTokenRepository.findByItineraryIdAndActiveTrue(ITINERARY_ID)).willReturn(Optional.empty());

            ThrowingCallable action = () -> itineraryService.delete(USER_ID, ITINERARY_ID);

            assertThatCode(action).doesNotThrowAnyException();
            verify(itineraryRepository).delete(itinerary);
        }
    }

    @Nested
    @DisplayName("generate - 혼잡 기반 순서변경 제안")
    class CongestionSuggestions {

        /** 스케줄러가 배정한 시각과 무관하게 검증하려고 모든 시간대에 같은 레벨을 채운다. */
        private Map<String, Map<Integer, CongestionLevel>> allHours(Map<String, CongestionLevel> byContentId) {
            Map<String, Map<Integer, CongestionLevel>> levels = new HashMap<>();
            byContentId.forEach((contentId, level) -> {
                Map<Integer, CongestionLevel> byHour = new HashMap<>();
                for (int hour = 0; hour < 24; hour++) {
                    byHour.put(hour, level);
                }
                levels.put(contentId, byHour);
            });
            return levels;
        }

        private void givenTwoPlaceItinerary() {
            Basket basket = basketWith(Region.HADONG, 2, "c1", "c2");
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));
            given(contentService.getContentDetail(anyString()))
                    .willAnswer(invocation -> detail(invocation.getArgument(0)));
            given(aiItineraryClient.generate(any())).willReturn(twoPlaceResult());
        }

        @Test
        @DisplayName("붐비는 장소 뒤에 덜 붐비는 장소가 있으면 순서를 바꾸자고 제안한다")
        void 트리거충족_제안생성() {
            givenTwoPlaceItinerary();
            given(congestionService.findLevels(any())).willReturn(allHours(Map.of(
                    "c1", CongestionLevel.HIGH,
                    "c2", CongestionLevel.LOW)));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.suggestions()).hasSize(1);
            ItineraryGenerateResponse.Suggestion suggestion = response.suggestions().get(0);
            assertThat(suggestion.type()).isEqualTo("CONGESTION_REORDER");
            assertThat(suggestion.dayIndex()).isEqualTo(1);
            assertThat(suggestion.contentId()).isEqualTo("c1");
            assertThat(suggestion.swapWithContentId()).isEqualTo("c2");
            assertThat(suggestion.message()).contains("title-c1", "title-c2", "붐빕니다");
            // 제안만 하고 실제 순서는 그대로 둔다 (수락은 PATCH /{id} 로 처리한다).
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
        }

        @Test
        @DisplayName("붐비는 장소가 없으면 제안하지 않는다")
        void 트리거미충족_제안없음() {
            givenTwoPlaceItinerary();
            given(congestionService.findLevels(any())).willReturn(allHours(Map.of(
                    "c1", CongestionLevel.MEDIUM,
                    "c2", CongestionLevel.LOW)));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.suggestions()).isEmpty();
        }

        @Test
        @DisplayName("뒤쪽 장소도 똑같이 붐비면 바꿀 이유가 없어 제안하지 않는다")
        void 대체후보없음_제안없음() {
            givenTwoPlaceItinerary();
            given(congestionService.findLevels(any())).willReturn(allHours(Map.of(
                    "c1", CongestionLevel.HIGH,
                    "c2", CongestionLevel.HIGH)));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.suggestions()).isEmpty();
        }

        @Test
        @DisplayName("혼잡 조회가 실패해도 일정 생성은 성공하고 제안은 빈 리스트가 된다")
        void 혼잡조회실패_생성성공() {
            givenTwoPlaceItinerary();
            given(congestionService.findLevels(any())).willThrow(new RuntimeException("congestion down"));

            ItineraryGenerateResponse response = itineraryService.generate(USER_ID);

            assertThat(response.days().get(0).items()).hasSize(2);
            assertThat(response.suggestions()).isEmpty();
        }
    }
}
