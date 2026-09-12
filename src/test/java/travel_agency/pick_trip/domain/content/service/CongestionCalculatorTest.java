package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.content.entity.CongestionLevel;

@DisplayName("CongestionCalculator")
class CongestionCalculatorTest {

    @Nested
    @DisplayName("percentile")
    class Percentile {

        @Test
        @DisplayName("자신보다 적게 담긴 콘텐츠 비율을 백분위로 돌려준다")
        void 담긴횟수_백분위() {
            // given
            List<Long> regionCounts = List.of(0L, 1L, 2L, 3L, 10L);

            // when & then
            assertThat(CongestionCalculator.percentile(10L, regionCounts)).isEqualTo(1.0);
            assertThat(CongestionCalculator.percentile(0L, regionCounts)).isEqualTo(0.0);
            assertThat(CongestionCalculator.percentile(2L, regionCounts)).isCloseTo(0.5, within(1e-9));
        }

        @Test
        @DisplayName("비교 대상이 1건 이하면 0.0 으로 본다")
        void 비교대상부족_0() {
            assertThat(CongestionCalculator.percentile(5L, List.of(5L))).isZero();
            assertThat(CongestionCalculator.percentile(5L, List.of())).isZero();
            assertThat(CongestionCalculator.percentile(5L, null)).isZero();
        }

        @Test
        @DisplayName("아무도 담지 않았으면 모두 0.0 이라 붐빔 경고가 나오지 않는다")
        void 담긴횟수전무_0() {
            List<Long> regionCounts = List.of(0L, 0L, 0L);

            double percentile = CongestionCalculator.percentile(0L, regionCounts);

            assertThat(percentile).isZero();
            assertThat(CongestionCalculator.level(CongestionCalculator.score(percentile, "12", 12)))
                    .isEqualTo(CongestionLevel.LOW);
        }
    }

    @Nested
    @DisplayName("시간대 가중")
    class Weight {

        @Test
        @DisplayName("음식점(39)은 점심 11~13시·저녁 18~20시가 피크다")
        void 음식점_피크시간대() {
            assertThat(CongestionCalculator.weight("39", 12)).isEqualTo(CongestionCalculator.PEAK_WEIGHT);
            assertThat(CongestionCalculator.weight("39", 19)).isEqualTo(CongestionCalculator.PEAK_WEIGHT);
            assertThat(CongestionCalculator.weight("39", 15)).isEqualTo(CongestionCalculator.OFF_PEAK_WEIGHT);
        }

        @Test
        @DisplayName("관광지(12)·문화시설(14)은 10~16시가 피크다")
        void 관광지_피크시간대() {
            assertThat(CongestionCalculator.weight("12", 10)).isEqualTo(CongestionCalculator.PEAK_WEIGHT);
            assertThat(CongestionCalculator.weight("14", 16)).isEqualTo(CongestionCalculator.PEAK_WEIGHT);
            assertThat(CongestionCalculator.weight("12", 19)).isEqualTo(CongestionCalculator.OFF_PEAK_WEIGHT);
        }

        @Test
        @DisplayName("피크 근거가 없는 타입은 시간대와 무관하게 평탄하다")
        void 기타타입_평탄() {
            for (int hourSlot : CongestionCalculator.SNAPSHOT_HOURS) {
                assertThat(CongestionCalculator.weight("32", hourSlot))
                        .isEqualTo(CongestionCalculator.FLAT_WEIGHT);
            }
            assertThat(CongestionCalculator.weight(null, 12)).isEqualTo(CongestionCalculator.FLAT_WEIGHT);
        }
    }

    @Nested
    @DisplayName("level")
    class Level {

        @Test
        @DisplayName("임계값 경계에서 레벨이 갈린다")
        void 임계값_경계() {
            assertThat(CongestionCalculator.level(CongestionCalculator.HIGH_THRESHOLD))
                    .isEqualTo(CongestionLevel.HIGH);
            assertThat(CongestionCalculator.level(CongestionCalculator.HIGH_THRESHOLD - 0.0001))
                    .isEqualTo(CongestionLevel.MEDIUM);
            assertThat(CongestionCalculator.level(CongestionCalculator.MEDIUM_THRESHOLD))
                    .isEqualTo(CongestionLevel.MEDIUM);
            assertThat(CongestionCalculator.level(CongestionCalculator.MEDIUM_THRESHOLD - 0.0001))
                    .isEqualTo(CongestionLevel.LOW);
        }

        @Test
        @DisplayName("같은 인기도라도 피크 시간대면 HIGH, 비피크면 한 단계 내려간다")
        void 피크여부에따라_레벨이달라진다() {
            // given: 지역 상위권(백분위 0.8) 관광지
            double percentile = 0.8;

            // when
            CongestionLevel peak = CongestionCalculator.level(
                    CongestionCalculator.score(percentile, "12", 13));
            CongestionLevel offPeak = CongestionCalculator.level(
                    CongestionCalculator.score(percentile, "12", 19));

            // then
            assertThat(peak).isEqualTo(CongestionLevel.HIGH);
            assertThat(offPeak).isEqualTo(CongestionLevel.MEDIUM);
        }

        @Test
        @DisplayName("점수는 1.0 을 넘지 않는다")
        void 점수는_1을넘지않는다() {
            assertThat(CongestionCalculator.score(1.0, "39", 12)).isEqualTo(1.0);
            assertThat(CongestionCalculator.score(2.0, "39", 12)).isEqualTo(1.0);
            assertThat(CongestionCalculator.score(-1.0, "39", 12)).isZero();
        }
    }
}
