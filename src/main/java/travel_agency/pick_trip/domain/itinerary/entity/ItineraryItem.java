package travel_agency.pick_trip.domain.itinerary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일차에 배치된 개별 장소. {@link ItineraryDay} 1 : N {@code ItineraryItem} 관계.
 * content 는 영속화하지 않으므로 TourAPI contentId 와 표시용 title 스냅샷을 저장하고,
 * AI 배치 이유(reason)와 사용자 고정 여부(isPinned)를 보관한다.
 */
@Getter
@Entity
@Table(name = "itinerary_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay day;

    @Column(name = "content_id", nullable = false, length = 50)
    private String contentId;

    @Column(length = 200)
    private String title;

    // 'order' 는 SQL 예약어이므로 컬럼명을 item_order 로 둔다.
    @Column(name = "item_order", nullable = false)
    private int orderIndex;

    @Column(length = 500)
    private String reason;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    // 스케줄러가 계산한 방문 시각. AI 응답에 시각이 없거나 좌표 미상이면 채우지 않으므로 nullable 이다.
    @Column(name = "visit_start")
    private LocalTime visitStart;

    @Column(name = "visit_end")
    private LocalTime visitEnd;

    @Builder
    private ItineraryItem(String contentId, String title, int orderIndex, String reason, boolean pinned,
                          LocalTime visitStart, LocalTime visitEnd) {
        this.contentId = contentId;
        this.title = title;
        this.orderIndex = orderIndex;
        this.reason = reason;
        this.pinned = pinned;
        this.visitStart = visitStart;
        this.visitEnd = visitEnd;
    }

    void assignDay(ItineraryDay day) {
        this.day = day;
    }
}
