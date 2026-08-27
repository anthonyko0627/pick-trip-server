package travel_agency.pick_trip.domain.basket.entity;

/**
 * 바구니에 담은 콘텐츠의 방문 우선순위.
 * AI 일정 생성 시 방문 순서·포함 여부 결정의 입력값으로 사용된다.
 *
 * <p>{@code label} 은 AI 프롬프트와 사용자 노출 문구에 쓰는 한국어 표현이다.
 * enum name(예: {@code MUST_VISIT})이 일정 배치 이유(reason) 등 사용자 화면에 새지 않도록,
 * AI 입력·후처리에서는 항상 이 라벨을 사용한다.
 */
public enum Priority {
    MUST_VISIT("꼭 가기"),
    PREFERRED("가면 좋음"),
    OPTIONAL("시간 남으면");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
