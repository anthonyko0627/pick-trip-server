package travel_agency.pick_trip.domain.home.dto.response;

import java.util.List;
import travel_agency.pick_trip.domain.region.Region;

/** 홈 화면 응답. Hero 통계, Hero 배경 이미지 목록, 지역 카드 목록으로 구성된다. */
public record HomeResponse(
        Stats stats,
        List<HeroImage> heroImages,
        List<RegionSummary> regions
) {
    /** Hero 섹션 상단 통계. */
    public record Stats(int regionCount, long contentCount) {}

    /** Hero 섹션 배경 이미지 1장. 어느 지역 사진인지 함께 내려준다. */
    public record HeroImage(Region region, String imageUrl) {}

    /** "어디부터 둘러볼까요?" 지역 카드 1장. imageUrl 은 이미지가 없으면 null. */
    public record RegionSummary(Region code, String name, String imageUrl) {}
}
