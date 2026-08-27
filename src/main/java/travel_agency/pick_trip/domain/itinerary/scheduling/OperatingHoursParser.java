package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TourAPI 의 {@code usetime} / {@code restdate} 자유 서술 문자열을 best-effort 로 파싱한다.
 * 원문 형식이 지자체마다 제각각이라 완전 파싱은 목표가 아니며,
 * 파싱 실패가 일정 생성 전체를 막지 않도록 어떤 입력에도 예외를 던지지 않고 unknown 을 돌려준다.
 */
public final class OperatingHoursParser {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    /**
     * 범위를 잇는 물결·붙임표. 지자체 원문이 제각각이라 실데이터에서 확인된 변형을 모두 받는다.
     * U+223C(∼ 수학 기호)는 예천온천 usetime 처럼 실제로 쓰이는데 흔히 빠뜨린다.
     */
    private static final String DASH = "[~\\-‐‑‒–—―－～∼〜]";

    /**
     * "09:00~18:00", "9:00 - 18:00", "09시~18시" 계열을 한 번에 잡는다.
     * 시·분 구분자 없이 "9~18" 처럼 쓰인 경우는 가격·인원수와 구별할 수 없어 일부러 매칭하지 않는다.
     */
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(\\d{1,2})\\s*(?::\\s*(\\d{1,2})|시)\\s*" + DASH
                    + "\\s*(\\d{1,2})\\s*(?::\\s*(\\d{1,2})|시)");

    /** 시각 제약이 없음을 명시한 표현. 인식은 성공한 것이므로 parsed 는 true 가 된다. */
    private static final Pattern ALWAYS_OPEN = Pattern.compile("상시개방|상시\\s*운영|24\\s*시간|연중\\s*무휴|제한\\s*없음");

    /** 휴무일이 없음을 명시한 표현. */
    private static final Pattern NO_REST = Pattern.compile("연중\\s*무휴|휴무\\s*없음|휴무일\\s*없음|쉬는\\s*날\\s*없음|^\\s*(없음|-|없슴)\\s*$");

    /**
     * "매월 첫째주 월요일", "매달 2, 4주째 일요일", "격주 화요일" 같은 월간·격주 규칙.
     * 매주 단위로 환원할 수 없어 휴무 요일로 확정하면 오히려 잘못된 일정이 나오므로 통째로 포기한다.
     * 서수를 하나씩 나열하면 반드시 빠뜨린다("다섯번째"를 놓쳐 매주 일요일 휴무로 확정한 적이 있다).
     */
    private static final Pattern NON_WEEKLY_RULE = Pattern.compile(
            "매월|매달|격주|홀수|짝수|마지막"
                    // 첫째 / 첫번째 / 다섯번째 … 한글 서수를 접미사 형태와 무관하게 잡는다.
                    + "|(?:첫|둘|두|셋|세|넷|네|다섯|여섯|일곱)\\s*(?:번)?\\s*째"
                    // "2, 4주째", "둘째 주", "3번째" 같은 숫자 서수.
                    + "|\\d+\\s*(?:주\\s*째|번\\s*째|째\\s*주)");

    private static final Pattern DAY_WITH_SUFFIX = Pattern.compile("([월화수목금토일])요일");

    /**
     * 괄호 안은 "(월요일이 공휴일인 경우 그 다음 화요일 휴관)" 처럼 예외 조항이라
     * 매주 확정 휴무가 아니다. 그대로 읽으면 영업일인 화요일까지 휴무로 잡힌다.
     */
    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*\\)");

    /** 휴무 절과 영업 절을 가르는 구분자. */
    private static final Pattern CLAUSE_DELIMITER = Pattern.compile("[,;/*·\\n]|단\\s|except", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLOSE_KEYWORD = Pattern.compile("휴무|휴관|휴점|휴업|쉬는\\s*날|정기\\s*휴|미\\s*운영|폐관");

    /** "화요일~일요일 정상 운영" 처럼 **영업일**을 나열한 절. 여기 있는 요일은 휴무가 아니다. */
    private static final Pattern OPEN_KEYWORD = Pattern.compile("정상|운영|영업|개관|개장|오픈|관람\\s*가능");

    /**
     * "화요일~일요일" 같은 요일 범위. 영업 키워드와 함께 쓰이면 영업일 나열이고,
     * restDate 문맥에서 영업 키워드 없이 쓰이면 휴무 범위다("매주 화요일~수요일").
     */
    private static final Pattern DAY_RANGE = Pattern.compile(
            "([월화수목금토일])(?:요일)?\\s*" + DASH + "\\s*([월화수목금토일])(?:요일)?");

    /** 휴게시간·매표시간이 운영시간보다 앞에 적힌 경우 그것을 개장~폐장으로 오인하지 않도록 거른다. */
    private static final Pattern BREAK_HINT = Pattern.compile(
            "휴게|브레이크|점심|중식|석식|식사|준비\\s*시간|매표|발권|접수|입장\\s*마감");

    /** "매주 월", "매주 월/화" 처럼 '요일' 접미사가 생략된 나열. '매일'·'매월'의 월/일과 섞이지 않도록 '매주' 뒤에서만 읽는다. */
    private static final Pattern WEEKLY_SHORT = Pattern.compile("매주\\s*((?:[월화수목금토일][\\s,·/및~와과]*)+)");

    private static final Map<Character, DayOfWeek> DAY_BY_CHAR = Map.of(
            '월', DayOfWeek.MONDAY,
            '화', DayOfWeek.TUESDAY,
            '수', DayOfWeek.WEDNESDAY,
            '목', DayOfWeek.THURSDAY,
            '금', DayOfWeek.FRIDAY,
            '토', DayOfWeek.SATURDAY,
            '일', DayOfWeek.SUNDAY
    );

    private OperatingHoursParser() {
    }

    public static OperatingHours parse(String useTime, String restDate) {
        try {
            String time = clean(useTime);
            String rest = clean(restDate);

            int[] hours = parseTimeRange(time);
            boolean timeRecognized = hours != null || (!time.isEmpty() && ALWAYS_OPEN.matcher(time).find());

            Set<DayOfWeek> closedDays = parseClosedDays(rest);
            boolean restRecognized = !closedDays.isEmpty() || (!rest.isEmpty() && NO_REST.matcher(rest).find());

            if (!timeRecognized && !restRecognized) {
                return OperatingHours.unknown();
            }
            return new OperatingHours(
                    hours == null ? null : hours[0],
                    hours == null ? null : hours[1],
                    closedDays,
                    true);
        } catch (RuntimeException e) {
            // 파싱 실패가 일정 생성을 막아서는 안 된다. 정보 없음으로 처리하고 넘어간다.
            return OperatingHours.unknown();
        }
    }

    /** @return {개장분, 폐장분} 또는 유효한 시간 범위를 못 찾으면 null */
    private static int[] parseTimeRange(String text) {
        Matcher m = TIME_RANGE.matcher(text);
        // 하절기/동절기처럼 여러 범위가 나열된 경우 계절 판단은 범위 밖이라
        // 유효한 첫 매칭을 쓰되, 휴게시간·매표시간이 앞서 나온 범위는 건너뛴다.
        while (m.find()) {
            String prefix = text.substring(Math.max(0, m.start() - 12), m.start());
            if (BREAK_HINT.matcher(prefix).find()) {
                continue;
            }
            Integer open = toMinuteOfDay(m.group(1), m.group(2));
            Integer close = toMinuteOfDay(m.group(3), m.group(4));
            if (open == null || close == null || open >= close) {
                // 22:00~02:00 같은 자정 넘김 운영은 하루 단위 스케줄링 모델로 표현할 수 없어 미상 처리한다.
                continue;
            }
            return new int[]{open, close};
        }
        return null;
    }

    private static Integer toMinuteOfDay(String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        int minute = (minuteText == null) ? 0 : Integer.parseInt(minuteText);
        if (hour > 23 || minute > 59) {
            return null;
        }
        return hour * 60 + minute;
    }

    /**
     * 요일을 문장 전체에서 무차별로 긁으면 "매주 월요일 휴관 (월요일이 공휴일이면 화요일 휴관)" 이나
     * "월요일 휴무, 화요일~일요일 정상 운영" 에서 영업일까지 휴무로 확정돼 잘못된 재배치를 만든다.
     * 그래서 예외 조항(괄호)과 영업일 서술 구간을 먼저 지워내고, 남은 원문에서 요일을 읽는다.
     * 못 읽는 쪽(제약 없음)은 검증을 건너뛸 뿐이지만, 잘못 읽는 쪽은 멀쩡한 일정을 망가뜨린다.
     * <p>
     * 구간을 골라 담지 않고 지워내는 이유는 한국어가 "수요일, 목요일 휴무" 처럼 키워드를 목록
     * 끝에 붙이고 "매주 월/화" 처럼 목록 구분자와 절 구분자가 같은 문자를 쓰기 때문이다.
     * 남은 부분은 원문 형태를 유지해야 이런 나열을 통째로 읽을 수 있다.
     */
    private static Set<DayOfWeek> parseClosedDays(String text) {
        String stripped = PARENTHESES.matcher(text).replaceAll(" ").trim();
        if (stripped.isEmpty() || NO_REST.matcher(stripped).find()) {
            return Set.of();
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        collectDays(maskNonClosingSegments(stripped), days);
        return days;
    }

    /** 영업일 나열·요일 범위·월간 규칙 구간을 같은 길이의 공백으로 덮어 원문 위치를 보존한다. */
    private static String maskNonClosingSegments(String text) {
        StringBuilder masked = new StringBuilder(text);
        Matcher delimiter = CLAUSE_DELIMITER.matcher(text);
        int from = 0;
        while (true) {
            boolean last = !delimiter.find();
            int to = last ? text.length() : delimiter.start();
            String segment = text.substring(from, to);
            boolean closing = CLOSE_KEYWORD.matcher(segment).find();
            // 요일 범위만으로는 영업일인지 휴무인지 알 수 없다. restDate 문맥이므로
            // 영업을 명시한 구간만 버리고, 나머지 범위는 휴무 범위로 읽는다.
            boolean describesOpenDays = !closing && OPEN_KEYWORD.matcher(segment).find();
            // 월간·격주 규칙은 구간 단위로만 버린다. 문장 전체를 버리면 같이 적힌 매주 휴무까지 놓친다.
            if (describesOpenDays || NON_WEEKLY_RULE.matcher(segment).find()) {
                masked.replace(from, to, " ".repeat(to - from));
            }
            if (last) {
                return masked.toString();
            }
            from = delimiter.end();
        }
    }

    private static void collectDays(String clause, Set<DayOfWeek> days) {
        // "매주 화요일~수요일" 은 화·수 이틀 휴무다. 양 끝만 담으면 사이 요일을 놓친다.
        Matcher range = DAY_RANGE.matcher(clause);
        while (range.find()) {
            DayOfWeek start = DAY_BY_CHAR.get(range.group(1).charAt(0));
            DayOfWeek end = DAY_BY_CHAR.get(range.group(2).charAt(0));
            if (start == null || end == null) {
                continue;
            }
            // "토요일~월요일" 처럼 주말을 가로지르면 한 바퀴 돈다. 7회로 끊어 무한 순회를 막는다.
            DayOfWeek cursor = start;
            for (int i = 0; i < DayOfWeek.values().length; i++) {
                days.add(cursor);
                if (cursor == end) {
                    break;
                }
                cursor = cursor.plus(1);
            }
        }
        Matcher withSuffix = DAY_WITH_SUFFIX.matcher(clause);
        while (withSuffix.find()) {
            days.add(DAY_BY_CHAR.get(withSuffix.group(1).charAt(0)));
        }
        Matcher shortForm = WEEKLY_SHORT.matcher(clause);
        while (shortForm.find()) {
            for (char c : shortForm.group(1).toCharArray()) {
                DayOfWeek day = DAY_BY_CHAR.get(c);
                if (day != null) {
                    days.add(day);
                }
            }
        }
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return HTML_TAG.matcher(raw).replaceAll(" ")
                .replace("&nbsp;", " ")
                // 웹 원문에서 넘어온 non-breaking space 는 정규식 \s 로 잡히지 않아 먼저 치환한다.
                .replace(' ', ' ')
                .trim();
    }
}
