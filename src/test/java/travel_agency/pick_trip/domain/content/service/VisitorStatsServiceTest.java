package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.basket.repository.projection.BasketContentCountProjection;
import travel_agency.pick_trip.domain.content.client.VisitorStatsClient;
import travel_agency.pick_trip.domain.content.client.dto.RegionVisitorResponse;
import travel_agency.pick_trip.domain.content.dto.response.VisitorStatsResponse;
import travel_agency.pick_trip.domain.content.entity.RegionVisitorStats;
import travel_agency.pick_trip.domain.content.repository.RegionVisitorStatsRepository;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("VisitorStatsService")
class VisitorStatsServiceTest {

    @Mock private VisitorStatsClient visitorStatsClient;
    @Mock private RegionVisitorStatsRepository regionVisitorStatsRepository;
    @Mock private BasketRepository basketRepository;

    @InjectMocks private VisitorStatsService visitorStatsService;

    private static final Region REGION = Region.YEONGJU; // 법정동 47 + 210
    private static final YearMonth MONTH = YearMonth.of(2026, 6);
    private static final int PAGE_SIZE = VisitorStatsService.PAGE_SIZE;

    // --- 테스트 헬퍼 ---

    private RegionVisitorResponse visitorResponse(int totalCount, RegionVisitorResponse.Item... items) {
        return new RegionVisitorResponse(new RegionVisitorResponse.Response(
                new RegionVisitorResponse.Header("0000", "OK"),
                new RegionVisitorResponse.Body(new RegionVisitorResponse.Items(List.of(items)), PAGE_SIZE, 1, totalCount)));
    }

    private RegionVisitorResponse.Item item(String signguCode, String touDivCd, Double touNum) {
        return new RegionVisitorResponse.Item(
                signguCode, "시군구", "1", "월요일", touDivCd, "구분", touNum, "20260601");
    }

    private RegionVisitorStats stats(Region region, String statMonth, long visitorCount) {
        return RegionVisitorStats.builder()
                .region(region)
                .statMonth(statMonth)
                .visitorCount(visitorCount)
                .source("한국관광공사 지역별 방문자수")
                .collectedAt(LocalDateTime.of(2026, 7, 2, 5, 0))
                .build();
    }

    private BasketContentCountProjection basketCount(String contentId, long count) {
        return new BasketContentCountProjection() {
            @Override
            public String getContentId() {
                return contentId;
            }

            @Override
            public long getBasketCount() {
                return count;
            }
        };
    }

    @Nested
    @DisplayName("collectMonth")
    class CollectMonth {

        @Test
        @DisplayName("전국 응답에서 우리 지역 행만 골라 현지인을 제외하고 지역별로 합산·저장한다")
        void collectMonth_전국응답_지역별합산저장() {
            // given
            given(visitorStatsClient.getLocalRegionVisitors("20260601", "20260630", 1, PAGE_SIZE))
                    .willReturn(visitorResponse(3,
                            item("11110", "2", 9999.0), // 종로구, 무시되어야 함
                            item("47210", "1", 500.0),  // 영주, 현지인 제외
                            item("47210", "2", 1000.0), // 영주
                            item("48850", "2", 250.4))); // 하동
            given(regionVisitorStatsRepository.findByRegionAndStatMonth(Region.YEONGJU, "2026-06"))
                    .willReturn(Optional.empty());
            given(regionVisitorStatsRepository.findByRegionAndStatMonth(Region.HADONG, "2026-06"))
                    .willReturn(Optional.empty());

            // when
            Map<Region, Long> collected = visitorStatsService.collectMonth(MONTH);

            // then
            assertThat(collected).containsOnly(
                    Map.entry(Region.YEONGJU, 1000L),
                    Map.entry(Region.HADONG, 250L));
            ArgumentCaptor<RegionVisitorStats> captor = ArgumentCaptor.forClass(RegionVisitorStats.class);
            verify(regionVisitorStatsRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(RegionVisitorStats::getRegion, RegionVisitorStats::getStatMonth,
                            RegionVisitorStats::getVisitorCount)
                    .containsExactlyInAnyOrder(
                            tuple(Region.YEONGJU, "2026-06", 1000L),
                            tuple(Region.HADONG, "2026-06", 250L));
        }

        @Test
        @DisplayName("totalCount 가 페이지 크기를 넘으면 다음 페이지를 이어 받아 합산하고 이후 페이지는 호출하지 않는다")
        void collectMonth_다음페이지_이어받아합산() {
            // given
            int totalCount = PAGE_SIZE + 500; // 1500 (PAGE_SIZE=1000)
            given(visitorStatsClient.getLocalRegionVisitors("20260601", "20260630", 1, PAGE_SIZE))
                    .willReturn(visitorResponse(totalCount, item("47210", "2", 500.0)));
            given(visitorStatsClient.getLocalRegionVisitors("20260601", "20260630", 2, PAGE_SIZE))
                    .willReturn(visitorResponse(totalCount, item("47210", "2", 700.0)));
            given(regionVisitorStatsRepository.findByRegionAndStatMonth(Region.YEONGJU, "2026-06"))
                    .willReturn(Optional.empty());

            // when
            Map<Region, Long> collected = visitorStatsService.collectMonth(MONTH);

            // then
            assertThat(collected).containsOnly(Map.entry(Region.YEONGJU, 1200L));
            verify(visitorStatsClient, never())
                    .getLocalRegionVisitors("20260601", "20260630", 3, PAGE_SIZE);
        }

        @Test
        @DisplayName("이미 수집한 연월이면 새로 저장하지 않고 기존 행을 갱신한다")
        void collectMonth_기존행_갱신() {
            // given
            RegionVisitorStats existing = stats(Region.YEONGJU, "2026-06", 10L);
            given(visitorStatsClient.getLocalRegionVisitors("20260601", "20260630", 1, PAGE_SIZE))
                    .willReturn(visitorResponse(1, item("47210", "2", 1000.0)));
            given(regionVisitorStatsRepository.findByRegionAndStatMonth(Region.YEONGJU, "2026-06"))
                    .willReturn(Optional.of(existing));

            // when
            visitorStatsService.collectMonth(MONTH);

            // then
            assertThat(existing.getVisitorCount()).isEqualTo(1000L);
            verify(regionVisitorStatsRepository, never()).save(any());
        }

        @Test
        @DisplayName("외부 API 호출이 실패해도 예외를 던지지 않고 빈 결과를 반환하며 저장하지 않는다")
        void collectMonth_호출실패_폴백() {
            // given (인증키 활용신청 전이거나 한도 초과인 상황)
            given(visitorStatsClient.getLocalRegionVisitors(anyString(), anyString(), anyInt(), anyInt()))
                    .willThrow(new FeignException(500, "visitor-stats 5xx") {});

            // when
            Map<Region, Long> collected = visitorStatsService.collectMonth(MONTH);

            // then
            assertThat(collected).isEmpty();
            verify(regionVisitorStatsRepository, never()).save(any());
        }

        @Test
        @DisplayName("오류 결과코드(HTTP 200) 응답이면 빈 결과를 반환하고 저장하지 않는다")
        void collectMonth_오류코드_미저장() {
            // given
            given(visitorStatsClient.getLocalRegionVisitors(anyString(), anyString(), anyInt(), anyInt()))
                    .willReturn(new RegionVisitorResponse(new RegionVisitorResponse.Response(
                            new RegionVisitorResponse.Header("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"),
                            null)));

            // when
            Map<Region, Long> collected = visitorStatsService.collectMonth(MONTH);

            // then
            assertThat(collected).isEmpty();
            verify(regionVisitorStatsRepository, never()).save(any());
        }

        @Test
        @DisplayName("2페이지째 호출이 실패하면 1페이지 합산분도 저장하지 않는다")
        void collectMonth_두번째페이지실패_과소집계방지() {
            // given
            int totalCount = PAGE_SIZE + 500;
            given(visitorStatsClient.getLocalRegionVisitors("20260601", "20260630", 1, PAGE_SIZE))
                    .willReturn(visitorResponse(totalCount, item("47210", "2", 500.0)));
            given(visitorStatsClient.getLocalRegionVisitors("20260601", "20260630", 2, PAGE_SIZE))
                    .willThrow(new FeignException(500, "visitor-stats 5xx") {});

            // when
            Map<Region, Long> collected = visitorStatsService.collectMonth(MONTH);

            // then
            assertThat(collected).isEmpty();
            verify(regionVisitorStatsRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findByContentIds")
    class FindByContentIds {

        @Test
        @DisplayName("지역 통계가 있으면 누적·일평균·기간을 매핑하고 근사 플래그를 세운다")
        void findByContentIds_지역통계_매핑() {
            // given
            given(regionVisitorStatsRepository.findByRegionOrderByStatMonthDesc(REGION))
                    .willReturn(List.of(stats(REGION, "2026-06", 100_000L), stats(REGION, "2026-05", 80_000L)));

            // when
            Map<String, VisitorStatsResponse> result =
                    visitorStatsService.findByContentIds(REGION, List.of("c1", "c2"));

            // then
            assertThat(result).containsOnlyKeys("c1", "c2");
            VisitorStatsResponse visitorStats = result.get("c1");
            assertThat(visitorStats.totalVisitors()).isEqualTo(180_000L);
            assertThat(visitorStats.dailyAverageVisitors()).isEqualTo(180_000L / 61);
            assertThat(visitorStats.period()).isEqualTo("2026-05~2026-06");
            assertThat(visitorStats.source()).isEqualTo("한국관광공사 지역별 방문자수");
            assertThat(visitorStats.baseDate()).isEqualTo(LocalDate.of(2026, 6, 30));
            // 지역 단위 통계를 콘텐츠 값으로 그대로 내려주므로 항상 근사값이다.
            assertThat(visitorStats.approximate()).isTrue();
            verify(basketRepository, never()).countByContentIds(any());
        }

        @Test
        @DisplayName("지역 통계가 없으면 바구니에 담긴 횟수(자체 프록시)로 폴백한다")
        void findByContentIds_지역통계없음_프록시폴백() {
            // given
            given(regionVisitorStatsRepository.findByRegionOrderByStatMonthDesc(REGION))
                    .willReturn(List.of());
            given(basketRepository.countByContentIds(List.of("c1", "c2")))
                    .willReturn(List.of(basketCount("c1", 7L)));

            // when
            Map<String, VisitorStatsResponse> result =
                    visitorStatsService.findByContentIds(REGION, List.of("c1", "c2"));

            // then: 담긴 적 없는 c2 는 아예 빠져 응답 필드가 null 이 된다.
            assertThat(result).containsOnlyKeys("c1");
            assertThat(result.get("c1").totalVisitors()).isEqualTo(7L);
            assertThat(result.get("c1").dailyAverageVisitors()).isNull();
            assertThat(result.get("c1").period()).isNull();
            assertThat(result.get("c1").source()).isEqualTo("PickTrip 내부 지표");
            assertThat(result.get("c1").approximate()).isTrue();
        }

        @Test
        @DisplayName("지역 통계도 프록시도 없으면 빈 결과를 돌려준다")
        void findByContentIds_통계도프록시도없음_빈결과() {
            // given
            given(regionVisitorStatsRepository.findByRegionOrderByStatMonthDesc(REGION))
                    .willReturn(List.of());
            given(basketRepository.countByContentIds(List.of("c1"))).willReturn(List.of());

            // when
            Map<String, VisitorStatsResponse> result =
                    visitorStatsService.findByContentIds(REGION, List.of("c1"));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("지역을 알 수 없으면 지역 통계를 조회하지 않고 프록시만 본다")
        void findByContentIds_지역모름_프록시만() {
            // given
            given(basketRepository.countByContentIds(List.of("c1")))
                    .willReturn(List.of(basketCount("c1", 3L)));

            // when
            Map<String, VisitorStatsResponse> result =
                    visitorStatsService.findByContentIds(null, List.of("c1"));

            // then
            assertThat(result.get("c1").source()).isEqualTo("PickTrip 내부 지표");
            verify(regionVisitorStatsRepository, never()).findByRegionOrderByStatMonthDesc(any());
        }
    }
}
