package travel_agency.pick_trip.domain.content.repository.projection;

/** 혼잡 스냅샷 계산 입력. 지역 내 콘텐츠와 그 콘텐츠가 바구니에 담긴 횟수(자체 인기 프록시). */
public interface ContentPopularityProjection {

    String getSourceContentId();

    String getContentTypeId();

    long getBasketCount();
}
