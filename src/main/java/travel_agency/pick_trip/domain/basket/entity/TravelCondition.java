package travel_agency.pick_trip.domain.basket.entity;

/**
 * 여행 동행·스타일 조건 (다중 선택 가능).
 * 사용자가 설정한 여행 조건의 일부로 바구니에 저장되며,
 * AI 일정 생성 시 동행 유형·걷기 부담·실내외 비율 등의 입력값으로 사용된다.
 *
 * <p>{@code label} 은 AI 프롬프트와 사용자 노출 문구에 쓰는 한국어 표현이다.
 * enum name(예: {@code LESS_WALKING})이 일정 배치 이유(reason) 등 사용자 화면에 새지 않도록,
 * AI 입력·후처리에서는 항상 이 라벨을 사용한다.
 */
public enum TravelCondition {
    WITH_CHILD("아이와 함께"),
    WITH_PARENTS("부모님과 함께"),
    WHOLE_FAMILY("가족 전체"),
    LESS_WALKING("걷기 적게"),
    NATURE_FOCUSED("자연 위주"),
    EXPERIENCE_FOCUSED("체험 위주"),
    FOOD_FOCUSED("음식 위주"),
    INDOOR_ALTERNATIVE("실내 대안 필요");

    private final String label;

    TravelCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
