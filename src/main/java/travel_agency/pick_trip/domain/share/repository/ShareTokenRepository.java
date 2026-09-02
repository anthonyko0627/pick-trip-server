package travel_agency.pick_trip.domain.share.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.share.entity.ShareToken;

@Repository
public interface ShareTokenRepository extends JpaRepository<ShareToken, UUID> {

    Optional<ShareToken> findByTokenAndActiveTrue(String token);

    Optional<ShareToken> findByItineraryIdAndActiveTrue(UUID itineraryId);

    /** 회원 하드 삭제 시 사용자 일정의 공유 토큰을 한 쿼리로 지운다. */
    @Modifying
    @Query("delete from ShareToken s where s.itineraryId in :itineraryIds")
    void deleteAllByItineraryIdIn(@Param("itineraryIds") Collection<UUID> itineraryIds);
}
