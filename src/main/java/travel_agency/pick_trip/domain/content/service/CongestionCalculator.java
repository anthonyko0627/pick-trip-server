package travel_agency.pick_trip.domain.content.service;

import java.util.List;
import travel_agency.pick_trip.domain.content.entity.CongestionLevel;

/**
 * 콘텐츠별 시간대 혼잡 레벨 산출 규칙. 외부 의존이 없는 순수 함수만 두어 단위 테스트로 규칙을 고정한다.
 *
 * <h2>산출 규칙</h2>
 * <ol>
 *   <li><b>인기도</b> — 같은 지역 콘텐츠들 중 바구니에 담긴 횟수의 백분위(0.0~1.0).
 *       개별 장소 단위 관광객수 공개 데이터가 없어 자체 프록시를 쓴다.</li>
 *   <li><b>시간대 가중</b> — 카테고리별 피크 시간대 휴리스틱.
 *       음식점(39)은 11~13시·18~20시, 관광지(12)·문화시설(14)은 10~16시가 피크이며
 *       피크는 가중 상향, 비피크는 하향한다. 그 외 타입은 근거가 없어 평탄(1.0)하게 둔다.</li>
 *   <li><b>레벨</b> — {@code score = 백분위 × 가중} 을 임계값으로 자른다.</li>
 * </ol>
 *
 * <p>ponytail: 실제 시간대별 관광객수(한국관광 데이터랩 관광지점 단위)가 공개 API 로 열리면
 * 이 휴리스틱을 실측값으로 교체한다.
 */
public final class CongestionCalculator {

    /**
     * 스냅샷을 남길 시간대. 하루 24시간을 모두 저장하면 콘텐츠 수 × 24 행이 쌓이는데
     * 새벽 시간대는 방문 일정에 배치되지 않아 쓰이지 않는다. 일정 스케줄러가 배치하는
     * 활동 시간대(09~21시)만 남긴다.
     */
    public static final List<Integer> SNAPSHOT_HOURS =
            List.of(9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);

    /**
     * 레벨 임계값. 가중 최댓값이 {@value #PEAK_WEIGHT} 이므로,
     * HIGH 는 "피크 시간대이면서 지역 상위 약 40% 안(0.75 / 1.3 ≒ 0.58 백분위)" 에서만 나오고
     * 비피크(가중 0.8)에서는 백분위 0.94 이상이라야 HIGH 가 된다. 즉 붐빔 경고가 남발되지 않는다.
     */
    public static final double HIGH_THRESHOLD = 0.75;

    /** MEDIUM 임계값. 평탄 가중(1.0)에서 지역 상위 55% 부터 MEDIUM 으로 본다. */
    public static final double MEDIUM_THRESHOLD = 0.45;

    /** 피크 시간대 가중. 임계값과 맞물려 "상위 40%+피크" 조합만 HIGH 가 되도록 잡았다. */
    public static final double PEAK_WEIGHT = 1.3;

    /** 비피크 시간대 가중. 같은 장소라도 피크를 피하면 한 단계 내려가도록 1.0 미만으로 둔다. */
    public static final double OFF_PEAK_WEIGHT = 0.8;

    /** 피크 시간대 근거가 없는 콘텐츠 타입의 가중. 시간대로 흔들지 않는다. */
    public static final double FLAT_WEIGHT = 1.0;

    /** TourAPI contentTypeId — 39: 음식점. */
    private static final String CONTENT_TYPE_RESTAURANT = "39";
    /** TourAPI contentTypeId — 12: 관광지, 14: 문화시설. */
    private static final List<String> CONTENT_TYPES_SIGHTSEEING = List.of("12", "14");

    private CongestionCalculator() {
    }

    /**
     * 지역 내 바구니 담긴 횟수 목록에서 {@code basketCount} 의 백분위를 구한다.
     * "자신보다 적게 담긴 콘텐츠 비율"이라 가장 적게 담긴 콘텐츠는 0.0 이 된다.
     * 모두 0건이면(수집 초기) 전부 0.0 → 전 지역 LOW 로 떨어져 잘못된 붐빔 경고를 내지 않는다.
     */
    public static double percentile(long basketCount, List<Long> regionCounts) {
        if (regionCounts == null || regionCounts.size() < 2) {
            return 0.0;
        }
        long fewer = regionCounts.stream().filter(count -> count < basketCount).count();
        return (double) fewer / (regionCounts.size() - 1);
    }

    /** 시간대 가중을 적용한 최종 점수 (0.0~1.0 으로 클램프). */
    public static double score(double popularityPercentile, String contentTypeId, int hourSlot) {
        double clamped = Math.min(Math.max(popularityPercentile, 0.0), 1.0);
        return Math.min(clamped * weight(contentTypeId, hourSlot), 1.0);
    }

    public static CongestionLevel level(double score) {
        if (score >= HIGH_THRESHOLD) {
            return CongestionLevel.HIGH;
        }
        if (score >= MEDIUM_THRESHOLD) {
            return CongestionLevel.MEDIUM;
        }
        return CongestionLevel.LOW;
    }

    /** 시간대 가중. 타입을 모르거나 피크 근거가 없는 타입은 평탄하게 둔다. */
    public static double weight(String contentTypeId, int hourSlot) {
        if (contentTypeId == null) {
            return FLAT_WEIGHT;
        }
        if (CONTENT_TYPE_RESTAURANT.equals(contentTypeId)) {
            return isRestaurantPeak(hourSlot) ? PEAK_WEIGHT : OFF_PEAK_WEIGHT;
        }
        if (CONTENT_TYPES_SIGHTSEEING.contains(contentTypeId)) {
            return (hourSlot >= 10 && hourSlot <= 16) ? PEAK_WEIGHT : OFF_PEAK_WEIGHT;
        }
        return FLAT_WEIGHT;
    }

    /** 점심 11~13시, 저녁 18~20시. */
    private static boolean isRestaurantPeak(int hourSlot) {
        return (hourSlot >= 11 && hourSlot <= 13) || (hourSlot >= 18 && hourSlot <= 20);
    }
}
