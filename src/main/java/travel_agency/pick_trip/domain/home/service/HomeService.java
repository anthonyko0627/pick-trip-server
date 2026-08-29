package travel_agency.pick_trip.domain.home.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.repository.projection.RegionImageProjection;
import travel_agency.pick_trip.domain.home.dto.response.HomeResponse;
import travel_agency.pick_trip.domain.region.Region;

// ponytail: 전체 이미지 행을 메모리에 올려 셔플한다. 콘텐츠가 수천 건을 넘어가면 지역별 LIMIT 쿼리로 분리한다.
@Service
@RequiredArgsConstructor
public class HomeService {

    private static final int HERO_IMAGES_PER_REGION = 2;

    private final TravelContentRepository travelContentRepository;

    @Transactional(readOnly = true)
    public HomeResponse getHome() {
        Map<Region, List<String>> imagesByRegion = groupByRegion(
                travelContentRepository.findRegionImages(DataStatus.ACTIVE));

        HomeResponse.Stats stats = new HomeResponse.Stats(
                Region.values().length,
                travelContentRepository.countByDataStatus(DataStatus.ACTIVE));

        List<HomeResponse.RegionSummary> regions = new ArrayList<>();
        List<HomeResponse.HeroImage> heroImages = new ArrayList<>();
        for (Region region : Region.values()) {
            List<String> images = imagesByRegion.getOrDefault(region, List.of());

            String thumbnailUrl = images.isEmpty() ? null : images.get(0);
            regions.add(new HomeResponse.RegionSummary(region, region.getName(), thumbnailUrl));

            for (String imageUrl : pickRandomHeroImages(images)) {
                heroImages.add(new HomeResponse.HeroImage(region, imageUrl));
            }
        }

        return new HomeResponse(stats, heroImages, regions);
    }

    private Map<Region, List<String>> groupByRegion(List<RegionImageProjection> images) {
        Map<Region, List<String>> result = new LinkedHashMap<>();
        for (RegionImageProjection image : images) {
            result.computeIfAbsent(image.region(), key -> new ArrayList<>()).add(image.imageUrl());
        }
        return result;
    }

    private List<String> pickRandomHeroImages(List<String> images) {
        List<String> shuffled = new ArrayList<>(images);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(HERO_IMAGES_PER_REGION, shuffled.size()));
    }
}
