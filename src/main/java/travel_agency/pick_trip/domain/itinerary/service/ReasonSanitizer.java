package travel_agency.pick_trip.domain.itinerary.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import travel_agency.pick_trip.domain.basket.entity.Priority;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;

/**
 * AI 가 생성한 배치 이유(reason) 문구에서 사용자가 이해할 수 없는 내부 값을 걷어낸다.
 * 시스템 프롬프트로 금지를 지시하지만 강제가 아니므로 서버에서 방어한다
 * ({@code filterToBasketContents} 와 동일한 방침).
 *
 * <p>제거·치환 대상:
 * <ul>
 *   <li>{@code contentId=773075} / {@code (contentId=773075)} 표기</li>
 *   <li>괄호 안 순수 숫자 4자리 이상 (TourAPI contentId 는 6~7자리)</li>
 *   <li>동행·우선순위 enum 코드({@code LESS_WALKING}, {@code less walking} 등) → 한국어 라벨</li>
 * </ul>
 */
final class ReasonSanitizer {

    /** {@code contentId=773075} / {@code contentId : 773075} / {@code 콘텐츠 ID 773075} (괄호 유무 무관) */
    private static final Pattern CONTENT_ID_TOKEN = Pattern.compile(
            "[(（]?\\s*(?:contentId|콘텐츠\\s*ID)\\s*[=:]?\\s*\\d+\\s*[)）]?",
            Pattern.CASE_INSENSITIVE);

    /** 괄호 안 순수 숫자 4자리 이상 (앞 공백까지 함께 제거) */
    private static final Pattern PARENTHESIZED_LONG_NUMBER =
            Pattern.compile("\\s*[(（]\\s*\\d{4,}\\s*[)）]");

    /** enum 코드 패턴 → 한국어 라벨 */
    private static final Map<Pattern, String> ENUM_TOKENS = buildEnumTokenMap();

    private ReasonSanitizer() {
    }

    static String sanitize(String reason) {
        if (reason == null || reason.isBlank()) {
            return reason;
        }
        String result = CONTENT_ID_TOKEN.matcher(reason).replaceAll("");
        result = PARENTHESIZED_LONG_NUMBER.matcher(result).replaceAll("");
        for (Map.Entry<Pattern, String> entry : ENUM_TOKENS.entrySet()) {
            result = entry.getKey().matcher(result)
                    .replaceAll(Matcher.quoteReplacement(entry.getValue()));
        }
        return tidy(result);
    }

    private static Map<Pattern, String> buildEnumTokenMap() {
        Map<Pattern, String> map = new LinkedHashMap<>();
        for (TravelCondition condition : TravelCondition.values()) {
            map.put(tokenPattern(condition.name()), condition.getLabel());
        }
        for (Priority priority : Priority.values()) {
            map.put(tokenPattern(priority.name()), priority.getLabel());
        }
        return map;
    }

    /**
     * enum name({@code LESS_WALKING})과 그 공백·하이픈 변형({@code less walking}, {@code less-walking})을
     * 단어 경계에서 대소문자 무시로 매칭한다.
     */
    private static Pattern tokenPattern(String enumName) {
        String core = enumName.replace("_", "[ _-]");
        return Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + core + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE);
    }

    private static String tidy(String text) {
        String result = text.replaceAll("\\s{2,}", " ");
        result = result.replaceAll("\\(\\s*\\)", "");
        result = result.replaceAll("\\s+([,.)\\]}])", "$1");
        result = result.replaceAll("([(\\[{])\\s+", "$1");
        return result.strip();
    }
}
