package travel_agency.pick_trip.domain.content.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.content.entity.ContentCongestion;
import travel_agency.pick_trip.domain.content.entity.CongestionLevel;
import travel_agency.pick_trip.domain.content.repository.ContentCongestionRepository;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.repository.projection.ContentPopularityProjection;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 콘텐츠별 시간대 혼잡 스냅샷 재계산·조회 (#73).
 * 산출 규칙은 {@link CongestionCalculator} 에 있고, 여기서는 입력을 모으고 결과를 저장·조회만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CongestionService {

    private final TravelContentRepository travelContentRepository;
    private final ContentCongestionRepository contentCongestionRepository;

    /**
     * 지역 콘텐츠의 혼잡 스냅샷을 다시 계산해 통째로 교체한다. 반환값은 저장한 스냅샷 행 수.
     *
     * <p>ponytail: 지역 전체를 지우고 다시 넣는 전량 재계산이다. 콘텐츠가 지역당 수백 건이고
     * 배치가 주 1회라 문제되지 않는다. 콘텐츠 수가 만 단위로 늘면 변경분만 갱신하도록 바꾼다.
     */
    @Transactional
    public int refreshRegion(Region region) {
        List<ContentPopularityProjection> rows = travelContentRepository.findPopularityByRegion(region.name());
        if (rows.isEmpty()) {
            log.info("[혼잡] {} 지역 콘텐츠가 없어 스냅샷을 건너뜁니다.", region);
            return 0;
        }

        List<Long> regionCounts = rows.stream()
                .map(ContentPopularityProjection::getBasketCount)
                .toList();
        contentCongestionRepository.deleteBySourceContentIds(
                rows.stream().map(ContentPopularityProjection::getSourceContentId).toList());

        LocalDateTime computedAt = LocalDateTime.now();
        List<ContentCongestion> snapshots = new ArrayList<>();
        for (ContentPopularityProjection row : rows) {
            double percentile = CongestionCalculator.percentile(row.getBasketCount(), regionCounts);
            for (int hourSlot : CongestionCalculator.SNAPSHOT_HOURS) {
                double score = CongestionCalculator.score(percentile, row.getContentTypeId(), hourSlot);
                snapshots.add(ContentCongestion.builder()
                        .sourceContentId(row.getSourceContentId())
                        .hourSlot(hourSlot)
                        .congestionLevel(CongestionCalculator.level(score))
                        .score(score)
                        .computedAt(computedAt)
                        .build());
            }
        }
        contentCongestionRepository.saveAll(snapshots);
        log.info("[혼잡] {} 지역 콘텐츠 {}건 · 스냅샷 {}건 저장", region, rows.size(), snapshots.size());
        return snapshots.size();
    }

    /**
     * 콘텐츠별 시간대 혼잡 레벨을 조회한다. 결과는 {@code contentId -> (시(hour) -> 레벨)} 형태이며,
     * 스냅샷이 없는 콘텐츠·시간대는 키 자체가 없다(호출 측에서 "모름"으로 다룬다).
     */
    @Transactional(readOnly = true)
    public Map<String, Map<Integer, CongestionLevel>> findLevels(Collection<String> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<Integer, CongestionLevel>> levels = new HashMap<>();
        for (ContentCongestion row : contentCongestionRepository.findBySourceContentIdIn(contentIds)) {
            levels.computeIfAbsent(row.getSourceContentId(), key -> new HashMap<>())
                    .put(row.getHourSlot(), row.getCongestionLevel());
        }
        return levels;
    }
}
