package travel_agency.pick_trip.domain.itinerary.dto.request;

/**
 * AI 일정 생성 모드.
 *
 * <p>{@link #STRICT} 는 바구니에 담은 장소만으로 일정을 구성하고,
 * {@link #AUGMENT} 는 AI 가 같은 지역의 적재 콘텐츠를 추가로 제안하도록 허용한다.
 * 어느 모드든 서버는 화이트리스트 밖(=DB 에 없는) 장소를 응답에서 제거한다.
 */
public enum GenerateMode {
    STRICT,
    AUGMENT
}
