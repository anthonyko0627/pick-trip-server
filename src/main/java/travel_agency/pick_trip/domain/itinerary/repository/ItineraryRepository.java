package travel_agency.pick_trip.domain.itinerary.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    /**
     * 일정을 일차와 함께 조회한다. EntityGraph 로 {@code days} 를 로딩해 N+1 을 방지한다.
     *
     * <p>PK 필드명이 {@code itineraryId}이므로 파생 쿼리({@code ...ById})로는 {@code id} 속성을 찾지 못한다.
     * 명시적 JPQL 로 PK 조건을 지정한다.
     *
     * <p>{@code days.items} 까지 함께 fetch 하면 List(bag) 두 개를 한 쿼리에서 조인하게 되어
     * Hibernate 가 {@code MultipleBagFetchException} 을 던진다. 항목은 배치 페치
     * ({@code hibernate.default_batch_fetch_size})로 IN 쿼리 한 번에 로딩한다.
     */
    @EntityGraph(attributePaths = {"days"})
    @Query("select i from Itinerary i where i.itineraryId = :itineraryId")
    Optional<Itinerary> findWithDaysById(@Param("itineraryId") UUID itineraryId);

    /**
     * 사용자의 일정 목록을 최근 수정 순으로 조회한다.
     * 목록은 요약 정보만 필요하므로 {@code days} 를 fetch 하지 않는다 (지연 로딩 유지).
     */
    List<Itinerary> findByUserIdOrderByLastModifiedAtDesc(UUID userId);
}
