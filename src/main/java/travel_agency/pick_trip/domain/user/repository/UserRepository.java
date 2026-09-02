package travel_agency.pick_trip.domain.user.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    /** {@code cutoff} 이전에 탈퇴해 유예 기간이 지난 계정의 uid 목록. 하드 삭제 배치(userPurgeJob) 대상. */
    @Query("select u.uid from User u where u.deleted = true and u.deletedAt <= :cutoff")
    List<UUID> findUidsWithdrawnBefore(@Param("cutoff") LocalDateTime cutoff);
}
