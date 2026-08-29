package travel_agency.pick_trip.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.repository.projection.RegionImageProjection;
import travel_agency.pick_trip.domain.home.dto.response.HomeResponse;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeService")
class HomeServiceTest {

    @Mock
    private TravelContentRepository travelContentRepository;

    @InjectMocks
    private HomeService homeService;

    @Test
    @DisplayName("지역마다 이미지가 3장 이상이면 heroImages는 지역별로 정확히 2장씩 총 6장이다")
    void returnsTwoHeroImagesPerRegion() {
        // given
        List<RegionImageProjection> images = List.of(
                new RegionImageProjection(Region.HADONG, "hadong-1.jpg"),
                new RegionImageProjection(Region.HADONG, "hadong-2.jpg"),
                new RegionImageProjection(Region.HADONG, "hadong-3.jpg"),
                new RegionImageProjection(Region.YEONGJU, "yeongju-1.jpg"),
                new RegionImageProjection(Region.YEONGJU, "yeongju-2.jpg"),
                new RegionImageProjection(Region.YEONGJU, "yeongju-3.jpg"),
                new RegionImageProjection(Region.YECHEON, "yecheon-1.jpg"),
                new RegionImageProjection(Region.YECHEON, "yecheon-2.jpg"),
                new RegionImageProjection(Region.YECHEON, "yecheon-3.jpg")
        );
        given(travelContentRepository.findRegionImages(DataStatus.ACTIVE)).willReturn(images);

        // when
        HomeResponse result = homeService.getHome();

        // then
        assertThat(result.heroImages()).hasSize(6);
        for (Region region : Region.values()) {
            long countForRegion = result.heroImages().stream()
                    .filter(hero -> hero.region() == region)
                    .count();
            assertThat(countForRegion).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("regions의 imageUrl은 해당 지역 조회 결과의 첫 번째 URL이다")
    void returnsFirstImageAsRegionThumbnail() {
        // given
        List<RegionImageProjection> images = List.of(
                new RegionImageProjection(Region.HADONG, "hadong-first.jpg"),
                new RegionImageProjection(Region.HADONG, "hadong-second.jpg"),
                new RegionImageProjection(Region.YEONGJU, "yeongju-first.jpg"),
                new RegionImageProjection(Region.YECHEON, "yecheon-first.jpg")
        );
        given(travelContentRepository.findRegionImages(DataStatus.ACTIVE)).willReturn(images);

        // when
        HomeResponse result = homeService.getHome();

        // then
        HomeResponse.RegionSummary hadong = findRegion(result, Region.HADONG);
        HomeResponse.RegionSummary yeongju = findRegion(result, Region.YEONGJU);
        HomeResponse.RegionSummary yecheon = findRegion(result, Region.YECHEON);

        assertThat(hadong.imageUrl()).isEqualTo("hadong-first.jpg");
        assertThat(yeongju.imageUrl()).isEqualTo("yeongju-first.jpg");
        assertThat(yecheon.imageUrl()).isEqualTo("yecheon-first.jpg");
    }

    @Test
    @DisplayName("특정 지역에 이미지가 없으면 imageUrl은 null이고 heroImages에 포함되지 않는다")
    void returnsNullImageWhenRegionHasNoImage() {
        // given
        List<RegionImageProjection> images = List.of(
                new RegionImageProjection(Region.HADONG, "hadong-first.jpg"),
                new RegionImageProjection(Region.YEONGJU, "yeongju-first.jpg")
        );
        given(travelContentRepository.findRegionImages(DataStatus.ACTIVE)).willReturn(images);

        // when
        HomeResponse result = homeService.getHome();

        // then
        HomeResponse.RegionSummary yecheon = findRegion(result, Region.YECHEON);
        assertThat(yecheon.imageUrl()).isNull();
        assertThat(result.heroImages()).noneMatch(hero -> hero.region() == Region.YECHEON);
    }

    @Test
    @DisplayName("stats는 Region 개수와 repository의 countByDataStatus 결과를 그대로 반영한다")
    void returnsStatsFromRepositoryCount() {
        // given
        given(travelContentRepository.findRegionImages(DataStatus.ACTIVE)).willReturn(List.of());
        given(travelContentRepository.countByDataStatus(DataStatus.ACTIVE)).willReturn(42L);

        // when
        HomeResponse result = homeService.getHome();

        // then
        assertThat(result.stats().regionCount()).isEqualTo(Region.values().length);
        assertThat(result.stats().contentCount()).isEqualTo(42L);
    }

    private HomeResponse.RegionSummary findRegion(HomeResponse result, Region region) {
        return result.regions().stream()
                .filter(summary -> summary.code() == region)
                .findFirst()
                .orElseThrow();
    }
}
