package travel_agency.pick_trip.domain.content.repository.projection;

/** AI 추가 제안 후보 제시용 (contentId, 장소명, 분류) 조회 전용 프로젝션. 상세는 필요 없다. */
public record RegionContentProjection(String contentId, String title, String contentTypeId) {
}
