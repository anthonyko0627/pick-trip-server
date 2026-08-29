package travel_agency.pick_trip.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.projection.NearbyContentProjection;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 실제 MySQL 로 {@code findNearby} 의 Haversine 거리 계산·반경 필터·정렬·제외 조건을 검증한다.
 * MySQL 삼각함수에 의존하므로 H2 로는 대체할 수 없다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TravelContentRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private TravelContentRepository travelContentRepository;

    @Autowired
    private TestEntityManager entityManager;

    // 기준 좌표: 위도 0.01도 ≈ 약 1.11km
    private static final double ORIGIN_LAT = 35.00;
    private static final double ORIGIN_LNG = 127.50;

    @Test
    @DisplayName("반경 안의 콘텐츠를 거리 오름차순으로 반환한다")
    void findNearby_returnsContentsWithinRadiusOrderedByDistance() {
        // given
        persist("origin", ORIGIN_LAT, ORIGIN_LNG, DataStatus.ACTIVE);
        persist("near", ORIGIN_LAT + 0.01, ORIGIN_LNG, DataStatus.ACTIVE);   // ≈ 1.1km
        persist("mid", ORIGIN_LAT + 0.03, ORIGIN_LNG, DataStatus.ACTIVE);    // ≈ 3.3km
        persist("far", ORIGIN_LAT + 0.30, ORIGIN_LNG, DataStatus.ACTIVE);    // ≈ 33km
        entityManager.flush();
        entityManager.clear();

        // when
        List<NearbyContentProjection> result =
                travelContentRepository.findNearby("origin", ORIGIN_LAT, ORIGIN_LNG, 10.0, 10);

        // then
        assertThat(result).extracting(NearbyContentProjection::getSourceContentId)
                .containsExactly("near", "mid");
        assertThat(result.get(0).getDistanceKm()).isLessThan(result.get(1).getDistanceKm());
        assertThat(result.get(0).getDistanceKm()).isBetween(1.0, 1.3);
    }

    @Test
    @DisplayName("기준 콘텐츠 자신·INACTIVE·좌표 없음·(0, 0) 콘텐츠는 제외한다")
    void findNearby_excludesOriginInactiveAndMissingCoordinates() {
        // given
        persist("origin", ORIGIN_LAT, ORIGIN_LNG, DataStatus.ACTIVE);
        persist("inactive", ORIGIN_LAT + 0.01, ORIGIN_LNG, DataStatus.INACTIVE);
        persist("noCoord", null, null, DataStatus.ACTIVE);
        persist("nullIsland", 0.0, 0.0, DataStatus.ACTIVE);
        persist("valid", ORIGIN_LAT + 0.01, ORIGIN_LNG, DataStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();

        // when
        List<NearbyContentProjection> result =
                travelContentRepository.findNearby("origin", ORIGIN_LAT, ORIGIN_LNG, 10.0, 10);

        // then
        assertThat(result).extracting(NearbyContentProjection::getSourceContentId)
                .containsExactly("valid");
    }

    @Test
    @DisplayName("size 만큼만 가장 가까운 순서로 잘라 반환한다")
    void findNearby_limitsToSize() {
        // given
        persist("origin", ORIGIN_LAT, ORIGIN_LNG, DataStatus.ACTIVE);
        persist("d1", ORIGIN_LAT + 0.01, ORIGIN_LNG, DataStatus.ACTIVE);
        persist("d2", ORIGIN_LAT + 0.02, ORIGIN_LNG, DataStatus.ACTIVE);
        persist("d3", ORIGIN_LAT + 0.03, ORIGIN_LNG, DataStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();

        // when
        List<NearbyContentProjection> result =
                travelContentRepository.findNearby("origin", ORIGIN_LAT, ORIGIN_LNG, 10.0, 2);

        // then
        assertThat(result).extracting(NearbyContentProjection::getSourceContentId)
                .containsExactly("d1", "d2");
    }

    private void persist(String id, Double latitude, Double longitude, DataStatus dataStatus) {
        entityManager.persist(TravelContent.builder()
                .sourceContentId(id)
                .contentTypeId("12")
                .title("장소 " + id)
                .region(Region.HADONG)
                .category(ContentCategory.CULTURE)
                .summary("요약 " + id)
                .address("경상남도 하동군")
                .latitude(latitude)
                .longitude(longitude)
                .firstImage("https://example.com/" + id + ".jpg")
                .dataStatus(dataStatus)
                .build());
    }
}
