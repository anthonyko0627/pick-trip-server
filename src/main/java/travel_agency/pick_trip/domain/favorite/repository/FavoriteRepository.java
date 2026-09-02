package travel_agency.pick_trip.domain.favorite.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    List<Favorite> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Favorite> findByUserIdAndContentId(UUID userId, String contentId);

    boolean existsByUserIdAndContentId(UUID userId, String contentId);

    /** 회원 하드 삭제 시 찜 목록을 한 쿼리로 지운다. */
    @Modifying
    @Query("delete from Favorite f where f.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
