package travel_agency.pick_trip.domain.itinerary.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정의 하루(일차). {@link Itinerary} 1 : N {@code ItineraryDay} 관계.
 */
@Getter
@Entity
@Table(name = "itinerary_days")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID dayId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @OneToMany(
            mappedBy = "day",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("orderIndex ASC")
    private List<ItineraryItem> items = new ArrayList<>();

    // 하루 이동 요약. 좌표가 없는 장소만 있으면 계산 불가하므로 nullable 이다.
    @Column(name = "travel_minutes")
    private Integer travelMinutes;

    @Column(name = "travel_km", precision = 6, scale = 2)
    private BigDecimal travelKm;

    @Builder
    private ItineraryDay(int dayIndex, Integer travelMinutes, BigDecimal travelKm) {
        this.dayIndex = dayIndex;
        this.travelMinutes = travelMinutes;
        this.travelKm = travelKm;
    }

    void assignItinerary(Itinerary itinerary) {
        this.itinerary = itinerary;
    }

    public void addItem(ItineraryItem item) {
        item.assignDay(this);
        this.items.add(item);
    }
}
