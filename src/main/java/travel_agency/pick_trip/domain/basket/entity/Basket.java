package travel_agency.pick_trip.domain.basket.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 사용자의 여행 바구니 (사용자당 1개).
 * 여행 조건(지역·날짜·기간·동행 조건)과 담은 콘텐츠 목록을 보관하며,
 * AI 일정 생성의 입력값이 된다.
 */
@Getter
@Entity
@Table(
        name = "baskets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_baskets_user_id", columnNames = {"user_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Basket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID basketId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // --- 여행 조건 (모두 선택값) ---
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Region region;

    @Column(name = "travel_date")
    private LocalDate travelDate;

    @Column
    private Integer duration;

    @ElementCollection
    @CollectionTable(
            name = "basket_companions",
            joinColumns = @JoinColumn(name = "basket_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "companion", length = 30)
    private Set<TravelCondition> companions = EnumSet.noneOf(TravelCondition.class);

    // --- 담은 콘텐츠 ---
    @OneToMany(
            mappedBy = "basket",
            cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true
    )
    private List<BasketItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Basket(UUID userId) {
        this.userId = userId;
    }

    /**
     * 여행 조건을 부분 갱신한다. {@code null} 인 필드는 "변경 없음"으로 보고 기존 값을 유지한다.
     *
     * <p>전체 교체 방식이던 시절에는 클라이언트가 필드를 빠뜨리면 저장돼 있던 값이 조용히 지워졌고,
     * 그 실패가 한참 뒤 일정 생성에서 {@code ITINERARY_INPUT_INSUFFICIENT} 로만 드러나
     * 원인을 찾기 어려웠다 (#43).
     *
     * <p>동행 조건만 예외로, 빈 집합을 명시적으로 보내면 비운다.
     */
    // ponytail: null 을 "변경 없음"으로 쓰므로 지역·날짜·기간을 다시 비울 방법이 없다.
    // 비우기가 필요해지면 JsonNullable 같은 래퍼로 필드 부재와 명시적 null 을 구분한다.
    public void updateConditions(
            Region region,
            LocalDate travelDate,
            Integer duration,
            Set<TravelCondition> companions
    ) {
        if (region != null) {
            this.region = region;
        }
        if (travelDate != null) {
            this.travelDate = travelDate;
        }
        if (duration != null) {
            this.duration = duration;
        }
        if (companions != null) {
            this.companions = companions.isEmpty()
                    ? EnumSet.noneOf(TravelCondition.class)
                    : EnumSet.copyOf(companions);
        }
    }

    public void addItem(BasketItem item) {
        item.assignBasket(this);
        this.items.add(item);
    }

    public void removeItem(BasketItem item) {
        this.items.remove(item);
    }

    public Optional<BasketItem> findItem(UUID itemId) {
        return this.items.stream()
                .filter(item -> item.getItemId().equals(itemId))
                .findFirst();
    }

    public boolean hasContent(String contentId) {
        return this.items.stream()
                .anyMatch(item -> item.getContentId().equals(contentId));
    }
}
