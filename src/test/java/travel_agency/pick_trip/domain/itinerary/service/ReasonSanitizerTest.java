package travel_agency.pick_trip.domain.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReasonSanitizer")
class ReasonSanitizerTest {

    @Nested
    @DisplayName("contentId·숫자 ID 제거")
    class RemoveIds {

        @Test
        @DisplayName("괄호로 감싼 contentId 표기를 제거한다")
        void removesLabeledContentId() {
            // given
            String reason = "쌍계사는 (contentId=773075) 근처라 함께 배치했습니다.";

            // when
            String result = ReasonSanitizer.sanitize(reason);

            // then
            assertThat(result)
                    .doesNotContainIgnoringCase("contentId")
                    .doesNotContain("773075")
                    .contains("쌍계사");
        }

        @Test
        @DisplayName("장소명 뒤에 붙은 괄호 숫자 ID를 제거하고 이름은 남긴다")
        void removesNameAdjacentNumericId() {
            // given
            String reason = "인근 슬로시티(773075)와 동선상 가까워 묶었습니다.";

            // when
            String result = ReasonSanitizer.sanitize(reason);

            // then
            assertThat(result).doesNotContain("773075").contains("슬로시티");
        }

        @Test
        @DisplayName("괄호 안 짧은 숫자(일차·시각 등)는 보존한다")
        void keepsShortParentheticalNumbers() {
            // given
            String reason = "오전 10시에 도착하도록 (2일차) 첫 일정으로 배치했습니다.";

            // when
            String result = ReasonSanitizer.sanitize(reason);

            // then
            assertThat(result).isEqualTo(reason);
        }
    }

    @Nested
    @DisplayName("enum 코드 → 한국어 라벨 치환")
    class ReplaceEnumCodes {

        @Test
        @DisplayName("동행 조건 enum name 을 라벨로 바꾼다")
        void replacesUnderscoreCode() {
            // given
            String reason = "LESS_WALKING 조건을 고려해 도보 이동이 짧은 곳으로 배치했습니다.";

            // when
            String result = ReasonSanitizer.sanitize(reason);

            // then
            assertThat(result).contains("걷기 적게").doesNotContain("LESS_WALKING");
        }

        @Test
        @DisplayName("공백으로 풀어 쓴 enum 코드도 라벨로 바꾼다")
        void replacesSpacedCode() {
            // given
            String reason = "less walking 을 고려해 오전에 배치했습니다.";

            // when
            String result = ReasonSanitizer.sanitize(reason);

            // then
            assertThat(result).contains("걷기 적게").doesNotContainIgnoringCase("less walking");
        }

        @Test
        @DisplayName("우선순위 enum name 을 라벨로 바꾼다")
        void replacesPriorityCode() {
            // given
            String reason = "MUST_VISIT 장소라서 첫날 오전에 우선 배치했습니다.";

            // when
            String result = ReasonSanitizer.sanitize(reason);

            // then
            assertThat(result).contains("꼭 가기").doesNotContain("MUST_VISIT");
        }
    }

    @Nested
    @DisplayName("정상 문구")
    class CleanText {

        @Test
        @DisplayName("내부 값이 없는 한국어 문장은 그대로 둔다")
        void keepsCleanSentence() {
            // given
            String reason = "축제 운영시간이 오전 10시부터라서 1일차 오전에 배치했습니다.";

            // when
            String result = ReasonSanitizer.sanitize(reason);

            // then
            assertThat(result).isEqualTo(reason);
        }

        @Test
        @DisplayName("제거 후 남은 이중 공백을 정리한다")
        void collapsesWhitespaceAfterRemoval() {
            // given
            String reason = "쌍계사 (contentId=773075) 근처";

            // when
            String result = ReasonSanitizer.sanitize(reason);

            // then
            assertThat(result).isEqualTo("쌍계사 근처");
        }

        @Test
        @DisplayName("null·빈 문자열은 그대로 반환한다")
        void passesThroughNullAndBlank() {
            assertThat(ReasonSanitizer.sanitize(null)).isNull();
            assertThat(ReasonSanitizer.sanitize("  ")).isEqualTo("  ");
        }
    }
}
