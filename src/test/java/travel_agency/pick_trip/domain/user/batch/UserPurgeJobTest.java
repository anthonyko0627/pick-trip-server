package travel_agency.pick_trip.domain.user.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;
import travel_agency.pick_trip.domain.favorite.repository.FavoriteRepository;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.domain.share.entity.ShareToken;
import travel_agency.pick_trip.domain.share.repository.ShareTokenRepository;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;

/**
 * 실제 MySQL 로 {@code userPurgeJob} 을 돌려 삭제 범위를 검증한다.
 * Job 이 자체 트랜잭션으로 커밋하므로 테스트에는 {@code @Transactional} 을 붙이지 않는다.
 */
@DisplayName("userPurgeJob")
@SpringBootTest
@Testcontainers
class UserPurgeJobTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job userPurgeJob;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private BasketRepository basketRepository;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private ShareTokenRepository shareTokenRepository;

    @Test
    @DisplayName("30일 지난 탈퇴 계정과 소유 데이터만 삭제하고 유예 중·활성 계정은 남긴다.")
    void purgesOnlyUsersWithdrawnBeforeRetention() throws Exception {
        // given
        User expired = saveUser("expired-" + UUID.randomUUID(), "탈퇴만료", 31);
        User grace = saveUser("grace-" + UUID.randomUUID(), "유예중", 1);
        User active = saveUser("active-" + UUID.randomUUID(), "활성", null);

        UUID uid = expired.getUid();
        refreshTokenRepository.save(RefreshToken.of(uid, "rt", LocalDateTime.now().plusDays(1)));
        favoriteRepository.save(Favorite.builder()
                .userId(uid)
                .contentId("c1")
                .title("t")
                .region(Region.HADONG)
                .build());
        basketRepository.save(Basket.builder().userId(uid).build());
        Itinerary itinerary = itineraryRepository.save(Itinerary.builder()
                .userId(uid)
                .title("하동 여행")
                .region(Region.HADONG)
                .travelDate(LocalDate.now())
                .duration(1)
                .build());
        shareTokenRepository.save(ShareToken.builder()
                .itineraryId(itinerary.getItineraryId())
                .token("share-token")
                .build());

        // when
        JobExecution execution = jobOperator.start(userPurgeJob, new JobParametersBuilder()
                .addLocalDateTime("run.time", LocalDateTime.now())
                .toJobParameters());
        awaitCompletion(execution);

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(userRepository.findById(uid)).isEmpty();
        assertThat(refreshTokenRepository.findById(uid)).isEmpty();
        assertThat(favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(uid)).isEmpty();
        assertThat(basketRepository.findByUserId(uid)).isEmpty();
        assertThat(itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(uid)).isEmpty();
        assertThat(shareTokenRepository.findByTokenAndActiveTrue("share-token")).isEmpty();

        assertThat(userRepository.findById(grace.getUid()))
                .get()
                .extracting(User::isDeleted)
                .isEqualTo(true);
        assertThat(userRepository.findById(active.getUid())).isPresent();
    }

    /** {@code withdrawnDaysAgo} 가 null 이면 활성 사용자, 아니면 그만큼 전에 탈퇴한 사용자를 저장한다. */
    private User saveUser(String providerUserId, String nickname, Integer withdrawnDaysAgo) {
        User user = User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .nickname(nickname)
                .build();
        if (withdrawnDaysAgo != null) {
            user.withdraw();
            ReflectionTestUtils.setField(
                    user, "deletedAt", LocalDateTime.now().minusDays(withdrawnDaysAgo));
        }
        return userRepository.save(user);
    }

    /** JobOperator 가 비동기로 구성돼 있어도 검증 전에 종료를 기다린다. */
    private static void awaitCompletion(JobExecution execution) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (execution.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }
}
