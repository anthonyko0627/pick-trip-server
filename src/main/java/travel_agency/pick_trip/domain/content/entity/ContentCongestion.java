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

/**
 * 콘텐츠별 시간대 혼잡 스냅샷. 배치가 지역 단위로 다시 계산해 통째로 교체한다.
 * 산출 규칙은 {@link travel_agency.pick_trip.domain.content.service.CongestionCalculator} 참고.
 */
@Getter
@Entity
@Table(
        name = "content_congestion",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_content_congestion_content_hour",
                        columnNames = {"source_content_id", "hour_slot"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentCongestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TourAPI contentid. {@code travel_contents} 와 FK 없이 값으로만 연결한다(콘텐츠는 보조 캐시라 사라질 수 있다). */
    @Column(name = "source_content_id", nullable = false, length = 50)
    private String sourceContentId;

    /** 0~23 시. 방문 가능 시간대만 저장한다({@code CongestionCalculator.SNAPSHOT_HOURS}). */
    @Column(name = "hour_slot", nullable = false)
    private int hourSlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_level", nullable = false)
    private CongestionLevel congestionLevel;

    @Column(nullable = false)
    private double score;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Builder
    private ContentCongestion(
            String sourceContentId,
            int hourSlot,
            CongestionLevel congestionLevel,
            double score,
            LocalDateTime computedAt
    ) {
        this.sourceContentId = sourceContentId;
        this.hourSlot = hourSlot;
        this.congestionLevel = congestionLevel;
        this.score = score;
        this.computedAt = computedAt;
    }
}
