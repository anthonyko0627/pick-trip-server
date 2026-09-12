package travel_agency.pick_trip.domain.itinerary.scheduling;

/**
 * 일정안을 만들 때 가정하는 이동수단.
 * 같은 장소 집합이라도 이동시간 모델이 달라 순서·일차 배분이 달라지므로, 모드마다 하나의 일정안(variant)이 나온다.
 *
 * <p>{@code label} 은 사용자에게 그대로 노출하는 일정안 이름이다.
 * enum name({@code CAR}, {@code TRANSIT})이 화면 문구로 새지 않도록 응답에는 이 라벨을 쓴다.
 */
public enum TravelMode {
    CAR("자동차 힐링 루트"),
    TRANSIT("뚜벅이 가성비 루트");

    private final String label;

    TravelMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
