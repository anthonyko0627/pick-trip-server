package travel_agency.pick_trip.gloal.scheduler;

import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.service.CongestionService;
import travel_agency.pick_trip.domain.content.service.VisitorStatsService;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 지역 방문자수 수집·혼잡 스냅샷 재계산 스케줄러 (#73).
 *
 * <p>{@code @Scheduled}는 {@code @EnableScheduling}이 활성화된 운영 프로파일에서만 동작한다
 * ({@link travel_agency.pick_trip.gloal.config.SchedulingConfig}). 두 작업은 주기가 달라
 * ({@link ContentSyncScheduler} 처럼) 한 잡으로 묶지 않고 따로 돈다. 어느 단계가 실패해도
 * 나머지 지역·단계는 계속 진행하며, 갱신 실패 시 기존 스냅샷을 그대로 유지한다.
 *
 * <p>Spring Batch Job 을 쓰지 않는다. 삭제 이력이 필요한 {@code userPurgeJob} 과 달리
 * 여기는 실행 이력을 남길 이유가 없어 스케줄러가 서비스 메서드를 호출하는 것으로 충분하다.
 *
 * <p>방문자수는 전국 응답 1회 호출로 세 지역을 함께 적재하므로 월 단위로만 돈다(#85).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorStatsScheduler {

    /**
     * 매 실행마다 되짚어 수집할 개월 수. 원천 통계는 공표가 한두 달 늦는 경우가 있어
     * 최근 몇 달을 다시 upsert 해 뒤늦게 채워진 값도 반영한다.
     */
    private static final int BACKFILL_MONTHS = 3;

    private final VisitorStatsService visitorStatsService;
    private final CongestionService congestionService;

    /** 지역별 방문자수 수집. 기본값: 매월 2일 새벽 5시. */
    @Scheduled(cron = "${visitor-stats.collect.cron:0 0 5 2 * *}")
    public void collectRegionVisitorStats() {
        log.info("[스케줄] 지역 방문자수 수집 시작");
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        for (int back = 0; back < BACKFILL_MONTHS; back++) {
            YearMonth month = lastMonth.minusMonths(back);
            runStep(() -> visitorStatsService.collectMonth(month), "방문자수 수집(" + month + ")");
        }
        log.info("[스케줄] 지역 방문자수 수집 완료");
    }

    /** 혼잡 스냅샷 재계산. 입력이 바구니 담긴 횟수라 콘텐츠 동기화보다 자주 바뀐다. 기본값: 매일 새벽 5시 30분. */
    @Scheduled(cron = "${congestion.refresh.cron:0 30 5 * * *}")
    public void refreshCongestionSnapshots() {
        log.info("[스케줄] 혼잡 스냅샷 재계산 시작");
        for (Region region : Region.values()) {
            runStep(() -> congestionService.refreshRegion(region), "혼잡 스냅샷 재계산(" + region + ")");
        }
        log.info("[스케줄] 혼잡 스냅샷 재계산 완료");
    }

    /** 한 단계의 예외를 격리해 나머지 단계가 중단되지 않게 한다. */
    private void runStep(Runnable step, String stepName) {
        try {
            step.run();
        } catch (RuntimeException e) {
            log.error("[스케줄] {} 단계 실패 - 건너뜀: {}", stepName, e.getMessage(), e);
        }
    }
}
