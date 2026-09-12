package travel_agency.pick_trip.domain.content.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.basket.repository.projection.BasketContentCountProjection;
import travel_agency.pick_trip.domain.content.client.VisitorStatsClient;
import travel_agency.pick_trip.domain.content.client.dto.RegionVisitorResponse;
import travel_agency.pick_trip.domain.content.dto.response.VisitorStatsResponse;
import travel_agency.pick_trip.domain.content.entity.RegionVisitorStats;
import travel_agency.pick_trip.domain.content.repository.RegionVisitorStatsRepository;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 지역 방문자수 수집과 콘텐츠 응답용 {@link VisitorStatsResponse} 조립 (#73).
 *
 * <p>개별 장소 단위 관광객수를 주는 공개 데이터가 없어 두 단계로 폴백한다.
 * <ol>
 *   <li>지역(시군구) 단위 방문자수 통계 — 수집 배치가 적재한 {@code region_visitor_stats}</li>
 *   <li>자체 프록시 — 그 콘텐츠가 바구니에 담긴 횟수</li>
 * </ol>
 * 둘 다 없으면 {@code null} 을 돌려 응답 필드를 비운다. 어느 경우든 근사값이므로
 * {@code approximate} 는 항상 {@code true} 다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorStatsService {

    /** 응답에 합산할 최근 개월 수. 계절 편차를 덮으면서 기간 표기가 길어지지 않는 선. */
    static final int STATS_MONTHS = 6;
    static final String SOURCE_PUBLIC_DATA = "한국관광공사 지역별 방문자수";
    static final String SOURCE_PROXY = "PickTrip 내부 지표";

    /** 전국 응답 페이지 순회 단위. 테스트가 참조하므로 package-private. */
    static final int PAGE_SIZE = 1000;
    // ponytail: 한 달 전국 ≈ 23페이지(31일 × ~247 시군구 × 3 구분 / 1000). 원천이 행을 더 쪼개면 상향한다.
    private static final int MAX_PAGES = 60;
    /** 관광객 구분 코드 1 = 현지인. 관광객수로 보기 어려워 합산에서 제외한다. */
    private static final String TOUR_DIV_LOCAL_RESIDENT = "1";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter STAT_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final VisitorStatsClient visitorStatsClient;
    private final RegionVisitorStatsRepository regionVisitorStatsRepository;
    private final BasketRepository basketRepository;

    /**
     * 한 달치 전국 방문자수를 페이지 순회로 받아 우리 지역(signguCode 일치)만 골라 (지역, 연월) 로 upsert 한다.
     * 반환값은 지역별 저장한 방문자수(저장하지 않은 지역은 키 없음).
     * 어떤 실패(예외·오류 코드·페이지 중간 실패)든 예외를 던지지 않고 아무것도 저장하지 않은 채 빈 맵을 반환한다.
     * 부분 페이지만 합산하면 과소 집계가 저장되므로 중간 실패 시 통째로 버린다.
     */
    @Transactional
    public Map<Region, Long> collectMonth(YearMonth month) {
        Map<String, Region> regionBySignguCode = new HashMap<>();
        for (Region region : Region.values()) {
            regionBySignguCode.put(region.getLDongRegnCd() + region.getLDongSignguCd(), region);
        }

        String startYmd = month.atDay(1).format(YMD);
        String endYmd = month.atEndOfMonth().format(YMD);
        Map<Region, Long> totals = new EnumMap<>(Region.class);
        int page = 1;
        while (true) {
            RegionVisitorResponse response;
            try {
                response = visitorStatsClient.getLocalRegionVisitors(startYmd, endYmd, page, PAGE_SIZE);
            } catch (RuntimeException e) {
                log.warn("[방문자수] {} {}페이지 조회 실패 - 건너뜀: {}", month, page, e.getMessage());
                return Map.of();
            }
            if (response == null || response.isError()) {
                log.warn("[방문자수] {} {}페이지 오류 응답 code={} msg={} - 건너뜀", month, page,
                        response == null ? null : response.resultCode(),
                        response == null ? null : response.resultMsg());
                return Map.of();
            }

            for (RegionVisitorResponse.Item item : response.items()) {
                Region region = regionBySignguCode.get(item.signguCode());
                if (region == null
                        || TOUR_DIV_LOCAL_RESIDENT.equals(item.touDivCd())
                        || item.touNum() == null) {
                    continue;
                }
                totals.merge(region, Math.round(item.touNum()), Long::sum);
            }

            if (response.items().isEmpty() || (long) page * PAGE_SIZE >= response.totalCount()) {
                break;
            }
            page++;
            if (page > MAX_PAGES) {
                log.warn("[방문자수] {} 페이지가 {}를 넘어 중단 - 건너뜀", month, MAX_PAGES);
                return Map.of();
            }
        }

        String statMonth = month.format(STAT_MONTH);
        LocalDateTime now = LocalDateTime.now();
        Map<Region, Long> saved = new EnumMap<>(Region.class);
        for (Map.Entry<Region, Long> entry : totals.entrySet()) {
            Region region = entry.getKey();
            long visitorCount = entry.getValue();
            if (visitorCount <= 0) {
                continue;
            }
            regionVisitorStatsRepository.findByRegionAndStatMonth(region, statMonth)
                    .ifPresentOrElse(
                            stats -> stats.updateCount(visitorCount, now),
                            () -> regionVisitorStatsRepository.save(RegionVisitorStats.builder()
                                    .region(region)
                                    .statMonth(statMonth)
                                    .visitorCount(visitorCount)
                                    .source(SOURCE_PUBLIC_DATA)
                                    .collectedAt(now)
                                    .build()));
            log.info("[방문자수] {} {} {}명 반영", region, statMonth, visitorCount);
            saved.put(region, visitorCount);
        }
        return saved;
    }

    /**
     * 콘텐츠별 관광객수 지표를 조립한다. 지표를 만들 수 없는 콘텐츠는 결과 맵에서 빠지며,
     * 호출 측은 그 콘텐츠의 {@code visitorStats} 를 {@code null} 로 응답한다.
     *
     * <p>지역 통계가 있으면 같은 지역의 모든 콘텐츠가 같은 값을 공유한다(지역 단위 통계라 그렇다).
     */
    @Transactional(readOnly = true)
    public Map<String, VisitorStatsResponse> findByContentIds(Region region, Collection<String> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Map.of();
        }
        VisitorStatsResponse regionStats = region == null ? null : toRegionStats(region);
        Map<String, VisitorStatsResponse> result = new HashMap<>();
        if (regionStats != null) {
            contentIds.forEach(contentId -> result.put(contentId, regionStats));
            return result;
        }

        LocalDate today = LocalDate.now();
        for (BasketContentCountProjection row : basketRepository.countByContentIds(contentIds)) {
            if (row.getBasketCount() > 0) {
                result.put(row.getContentId(), new VisitorStatsResponse(
                        row.getBasketCount(), null, null, SOURCE_PROXY, today, true));
            }
        }
        return result;
    }

    /** 최근 {@value #STATS_MONTHS} 개월 통계를 누적·일평균으로 요약한다. 적재된 통계가 없으면 null. */
    private VisitorStatsResponse toRegionStats(Region region) {
        List<RegionVisitorStats> recent = regionVisitorStatsRepository
                .findByRegionOrderByStatMonthDesc(region).stream()
                .limit(STATS_MONTHS)
                .toList();
        if (recent.isEmpty()) {
            return null;
        }

        long total = recent.stream().mapToLong(RegionVisitorStats::getVisitorCount).sum();
        YearMonth latest = YearMonth.parse(recent.get(0).getStatMonth());
        YearMonth oldest = YearMonth.parse(recent.get(recent.size() - 1).getStatMonth());
        LocalDate baseDate = latest.atEndOfMonth();
        long days = ChronoUnit.DAYS.between(oldest.atDay(1), baseDate) + 1;

        return new VisitorStatsResponse(
                total,
                days > 0 ? total / days : null,
                oldest + "~" + latest,
                SOURCE_PUBLIC_DATA,
                baseDate,
                true
        );
    }
}
