package travel_agency.pick_trip.domain.basket.entity;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.region.Region;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Basket")
class BasketTest {

    private static final Region REGION = Region.YEONGJU;
    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 8, 20);
    private static final int DURATION = 3;

    /** 네 가지 조건이 모두 채워진 바구니. */
    private Basket basketWithAllConditions() {
        Basket basket = Basket.builder()
                .userId(UUID.randomUUID())
                .build();
        basket.updateConditions(REGION, TRAVEL_DATE, DURATION, Set.of(TravelCondition.WITH_PARENTS));
        return basket;
    }

    @Nested
    @DisplayName("updateConditions")
    class UpdateConditions {

        /**
         * #43 재발 방지. 클라이언트가 duration 을 빠뜨린 요청으로 저장된 값이 지워지면서,
         * 실패가 한참 뒤 일정 생성에서야 드러났다.
         */
        @Test
        @DisplayName("duration 을 빼고 갱신해도 기존 duration 을 유지한다.")
        void keepDurationWhenOmitted() {
            // given
            Basket basket = basketWithAllConditions();

            // when
            basket.updateConditions(Region.HADONG, LocalDate.of(2026, 9, 1), null,
                    Set.of(TravelCondition.WITH_CHILD));

            // then
            assertThat(basket.getDuration()).isEqualTo(DURATION);
            assertThat(basket.getRegion()).isEqualTo(Region.HADONG);
        }

        @Test
        @DisplayName("모든 조건을 빼고 갱신하면 기존 조건이 그대로 남는다.")
        void keepAllConditionsWhenAllOmitted() {
            // given
            Basket basket = basketWithAllConditions();

            // when
            basket.updateConditions(null, null, null, null);

            // then
            assertThat(basket.getRegion()).isEqualTo(REGION);
            assertThat(basket.getTravelDate()).isEqualTo(TRAVEL_DATE);
            assertThat(basket.getDuration()).isEqualTo(DURATION);
            assertThat(basket.getCompanions()).containsExactly(TravelCondition.WITH_PARENTS);
        }

        @Test
        @DisplayName("전달한 조건만 바꾸고 나머지는 유지한다.")
        void updateOnlyProvidedConditions() {
            // given
            Basket basket = basketWithAllConditions();

            // when
            basket.updateConditions(null, null, 5, null);

            // then
            assertThat(basket.getDuration()).isEqualTo(5);
            assertThat(basket.getRegion()).isEqualTo(REGION);
            assertThat(basket.getTravelDate()).isEqualTo(TRAVEL_DATE);
            assertThat(basket.getCompanions()).containsExactly(TravelCondition.WITH_PARENTS);
        }

        @Test
        @DisplayName("동행 조건에 빈 집합을 보내면 비운다.")
        void clearCompanionsWhenEmptySetGiven() {
            // given
            Basket basket = basketWithAllConditions();

            // when
            basket.updateConditions(null, null, null, Set.of());

            // then
            assertThat(basket.getCompanions()).isEmpty();
            assertThat(basket.getDuration()).isEqualTo(DURATION);
        }

        @Test
        @DisplayName("조건이 비어 있던 바구니는 전달한 값으로 채워진다.")
        void fillConditionsWhenBasketIsEmpty() {
            // given
            Basket basket = Basket.builder()
                    .userId(UUID.randomUUID())
                    .build();

            // when
            basket.updateConditions(REGION, TRAVEL_DATE, DURATION, Set.of(TravelCondition.WITH_PARENTS));

            // then
            assertThat(basket.getRegion()).isEqualTo(REGION);
            assertThat(basket.getTravelDate()).isEqualTo(TRAVEL_DATE);
            assertThat(basket.getDuration()).isEqualTo(DURATION);
            assertThat(basket.getCompanions()).containsExactly(TravelCondition.WITH_PARENTS);
        }
    }
}
