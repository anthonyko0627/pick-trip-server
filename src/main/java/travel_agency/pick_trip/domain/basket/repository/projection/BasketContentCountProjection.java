package travel_agency.pick_trip.domain.basket.repository.projection;

/** 콘텐츠가 바구니에 담긴 횟수. 개별 장소 단위 관광객수 공개 데이터가 없어 자체 인기 프록시로 쓴다 (#73). */
public interface BasketContentCountProjection {

    String getContentId();

    long getBasketCount();
}
