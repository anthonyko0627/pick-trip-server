package travel_agency.pick_trip.domain.content.repository.projection;

/**
 * 좌표 기반 주변 조회 전용 프로젝션. 네이티브 쿼리가 계산한 {@code distanceKm}(Haversine 근사, km)를 함께 담는다.
 * 네이티브 쿼리의 컬럼 별칭이 아래 getter 이름과 일치해야 한다.
 */
public interface NearbyContentProjection {

    String getSourceContentId();

    String getContentTypeId();

    String getTitle();

    String getAddress();

    String getFirstImage();

    Double getLatitude();

    Double getLongitude();

    String getCategory();

    String getSummary();

    String getRegion();

    Double getDistanceKm();
}
