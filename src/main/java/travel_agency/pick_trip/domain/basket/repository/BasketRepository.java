package travel_agency.pick_trip.domain.basket.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.basket.entity.Basket;

@Repository
public interface BasketRepository extends JpaRepository<Basket, UUID> {

    /**
     * 사용자 바구니를 항목과 함께 조회한다.
     * EntityGraph로 items를 함께 로딩해 N+1을 방지한다.
     *
     * <p>companions는 일부러 제외한다. items(bag)와 companions를 한 쿼리에서 join fetch 하면
     * 결과가 카테시안 곱이 되어 items가 companions 개수만큼 중복됐고,
     * 그 탓에 {@code Basket.removeItem} 의 {@code List.remove} 가 사본 하나만 지워
     * orphanRemoval 이 발동하지 않아 삭제한 항목이 DB에 그대로 남았다 (#54).
     * companions는 LAZY 로 별도 select 한 번에 로딩되며, 바구니는 사용자당 1개라 N+1이 아니다.
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<Basket> findByUserId(UUID userId);
}
