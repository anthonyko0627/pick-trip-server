package travel_agency.pick_trip.domain.content.repository.projection;

import travel_agency.pick_trip.domain.region.Region;

/** 홈 화면 이미지 선정을 위한 (지역, 대표 이미지 URL) 조회 전용 프로젝션. */
public record RegionImageProjection(Region region, String imageUrl) {
}
