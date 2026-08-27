package travel_agency.pick_trip.domain.itinerary.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryDay;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryItem;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 실제 MySQL 로 {@code findWithDaysById} 의 fetch 전략을 검증한다.
 * 일차·항목을 함께 로딩하는 쿼리는 공유 링크 생성·일정 수정의 진입점이므로 회귀를 막는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ItineraryRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("일차와 항목을 한 번에 조회한다.")
    void findWithDaysByIdLoadsDaysAndItems() {
        // given
        Itinerary itinerary = Itinerary.builder()
                .userId(UUID.randomUUID())
                .title("하동 2일 여행")
                .region(Region.HADONG)
                .travelDate(LocalDate.of(2026, 8, 9))
                .duration(2)
                .build();
        itinerary.addDay(dayOf(1, "1001", "1002"));
        itinerary.addDay(dayOf(2, "1003"));
        UUID itineraryId = itineraryRepository.save(itinerary).getItineraryId();
        entityManager.flush();
        entityManager.clear();

        // when
        Itinerary found = itineraryRepository.findWithDaysById(itineraryId).orElseThrow();

        // then
        assertThat(found.getDays()).hasSize(2);
        assertThat(found.getDays().get(0).getItems()).hasSize(2);
        assertThat(found.getDays().get(1).getItems()).hasSize(1);
    }

    @Test
    @DisplayName("방문 시각과 이동 요약을 저장하고 다시 조회한다.")
    void persistsVisitTimesAndTravelSummary() {
        // given
        Itinerary itinerary = Itinerary.builder()
                .userId(UUID.randomUUID())
                .title("영주 1일 여행")
                .region(Region.YEONGJU)
                .travelDate(LocalDate.of(2026, 8, 9))
                .duration(1)
                .build();
        ItineraryDay day = ItineraryDay.builder()
                .dayIndex(1)
                .travelMinutes(42)
                .travelKm(new BigDecimal("18.75"))
                .build();
        day.addItem(ItineraryItem.builder()
                .contentId("2001")
                .title("부석사")
                .orderIndex(1)
                .reason("테스트")
                .pinned(false)
                .visitStart(LocalTime.of(9, 0))
                .visitEnd(LocalTime.of(10, 30))
                .build());
        itinerary.addDay(day);
        UUID itineraryId = itineraryRepository.save(itinerary).getItineraryId();
        entityManager.flush();
        entityManager.clear();

        // when
        Itinerary found = itineraryRepository.findWithDaysById(itineraryId).orElseThrow();

        // then
        ItineraryDay foundDay = found.getDays().get(0);
        assertThat(foundDay.getTravelMinutes()).isEqualTo(42);
        assertThat(foundDay.getTravelKm()).isEqualByComparingTo("18.75");
        assertThat(foundDay.getItems().get(0).getVisitStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(foundDay.getItems().get(0).getVisitEnd()).isEqualTo(LocalTime.of(10, 30));
    }

    private ItineraryDay dayOf(int dayIndex, String... contentIds) {
        ItineraryDay day = ItineraryDay.builder().dayIndex(dayIndex).build();
        int order = 1;
        for (String contentId : contentIds) {
            day.addItem(ItineraryItem.builder()
                    .contentId(contentId)
                    .title("장소 " + contentId)
                    .orderIndex(order++)
                    .reason("테스트")
                    .pinned(false)
                    .build());
        }
        return day;
    }
}
