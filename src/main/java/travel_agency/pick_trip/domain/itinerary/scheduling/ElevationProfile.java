package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.util.Locale;
import java.util.Map;

/**
 * 한 번의 일정 생성 안에서 조회한 좌표별 해발고도(m)를 담는 값 객체다.
 *
 * <p>같은 좌표를 여러 leg 이 공유하므로(한 장소는 앞 leg 의 도착이자 다음 leg 의 출발이다)
 * 조회 결과를 여기 모아두고 재조회하지 않는다. 수명은 요청 하나이며 캐시 인프라를 따로 두지 않는다.
 *
 * <p>고도를 모르는 좌표는 아예 담지 않는다. {@link #metersAt}가 {@code null}을 돌려주면 "고도 미상"이고,
 * {@link WalkEffort}는 그 구간을 상승고도 0·페널티 0 으로 취급한다.
 */
public record ElevationProfile(Map<String, Double> metersByCoordinate) {

    private static final ElevationProfile EMPTY = new ElevationProfile(Map.of());

    public ElevationProfile {
        // 외부에서 넘긴 Map 이 나중에 바뀌어도 프로필이 흔들리지 않도록 방어 복사한다.
        metersByCoordinate = metersByCoordinate == null ? Map.of() : Map.copyOf(metersByCoordinate);
    }

    /** 조회 실패·미설정 시의 빈 프로필. 모든 좌표가 "고도 미상"으로 취급된다. */
    public static ElevationProfile unknown() {
        return EMPTY;
    }

    /**
     * 좌표를 키 문자열로 정규화한다. 적재와 조회가 같은 규칙을 써야 하므로 여기 한 곳에만 둔다.
     * 소수점 6자리는 약 11cm 해상도로, 어떤 고도 데이터셋(30m 격자)보다도 촘촘해 충돌 걱정이 없다.
     */
    public static String key(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude);
    }

    /** 해발고도(m). 좌표가 없거나 조회하지 못한 지점이면 {@code null}(= 고도 미상). */
    public Double metersAt(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return metersByCoordinate.get(key(latitude, longitude));
    }
}
