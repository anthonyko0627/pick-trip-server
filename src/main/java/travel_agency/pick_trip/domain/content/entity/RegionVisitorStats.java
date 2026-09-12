package travel_agency.pick_trip.domain.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 지역(시군구)·기준 연월 단위 방문자수 스냅샷. 공공데이터포털 "한국관광공사 빅데이터 지역별 방문자수"
 * (15101972)를 수집 배치가 월 단위로 합산해 저장한다.
 *
 * <p>개별 장소 단위 통계가 아니므로 콘텐츠 응답에 내려줄 때는 항상 근사값({@code approximate=true})으로
 * 표시한다.
 */
@Getter
@Entity
@Table(
        name = "region_visitor_stats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_region_visitor_stats_region_month",
                        columnNames = {"region", "stat_month"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionVisitorStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    /** 기준 연월 (yyyy-MM). 원천이 일 단위라 월 단위로 합산해 보관한다. */
    @Column(name = "stat_month", nullable = false, length = 7)
    private String statMonth;

    @Column(name = "visitor_count", nullable = false)
    private long visitorCount;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Builder
    private RegionVisitorStats(
            Region region,
            String statMonth,
            long visitorCount,
            String source,
            LocalDateTime collectedAt
    ) {
        this.region = region;
        this.statMonth = statMonth;
        this.visitorCount = visitorCount;
        this.source = source;
        this.collectedAt = collectedAt;
    }

    /** 재수집 시 같은 (지역, 연월) 행을 갱신한다. 원천이 뒤늦게 값을 정정하는 경우가 있다. */
    public void updateCount(long visitorCount, LocalDateTime collectedAt) {
        this.visitorCount = visitorCount;
        this.collectedAt = collectedAt;
    }
}
