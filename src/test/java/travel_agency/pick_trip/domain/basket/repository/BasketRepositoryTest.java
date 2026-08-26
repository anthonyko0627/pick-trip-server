package travel_agency.pick_trip.domain.basket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;
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
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.basket.entity.Priority;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 실제 MySQL 로 {@code findByUserId} 의 fetch 전략을 검증한다.
 * items 와 companions 를 한 쿼리에서 join fetch 하면 결과가 카테시안 곱이 되어
 * items 가 companions 개수만큼 중복됐고, 그 중복이 항목 삭제까지 무력화했으므로 회귀를 막는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class BasketRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private BasketRepository basketRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("동행 조건이 여러 개여도 항목을 중복 없이 조회한다.")
    void findByUserIdDoesNotDuplicateItems() {
        // given
        UUID userId = saveBasket("1001", "1002");

        // when
        Basket found = basketRepository.findByUserId(userId).orElseThrow();

        // then
        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getItems())
                .extracting(BasketItem::getContentId)
                .containsExactlyInAnyOrder("1001", "1002");
        assertThat(found.getCompanions()).hasSize(2);
    }

    @Test
    @DisplayName("동행 조건이 여러 개여도 제거한 항목이 DB에서 삭제된다.")
    void removeItemDeletesRowWhenMultipleCompanions() {
        // given
        UUID userId = saveBasket("1001", "1002");
        Basket basket = basketRepository.findByUserId(userId).orElseThrow();
        BasketItem target = basket.getItems().stream()
                .filter(item -> item.getContentId().equals("1001"))
                .findFirst()
                .orElseThrow();

        // when
        basket.removeItem(target);
        entityManager.flush();
        entityManager.clear();

        // then
        Basket reloaded = basketRepository.findByUserId(userId).orElseThrow();
        assertThat(reloaded.getItems())
                .extracting(BasketItem::getContentId)
                .containsExactly("1002");
    }

    /**
     * 동행 조건 2개짜리 바구니를 저장한다. 조건이 2개 이상일 때만 중복이 드러난다.
     */
    private UUID saveBasket(String... contentIds) {
        UUID userId = UUID.randomUUID();
        Basket basket = Basket.builder().userId(userId).build();
        basket.updateConditions(
                Region.HADONG,
                LocalDate.of(2026, 8, 9),
                2,
                EnumSet.of(TravelCondition.WITH_CHILD, TravelCondition.LESS_WALKING)
        );
        for (String contentId : contentIds) {
            basket.addItem(BasketItem.builder()
                    .contentId(contentId)
                    .title("장소 " + contentId)
                    .thumbnailUrl("https://example.com/" + contentId + ".jpg")
                    .contentTypeId("12")
                    .priority(Priority.MUST_VISIT)
                    .build());
        }
        basketRepository.save(basket);
        entityManager.flush();
        entityManager.clear();
        return userId;
    }
}
