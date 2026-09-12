package travel_agency.pick_trip.domain.itinerary.dto.request;

import java.util.List;
import java.util.Objects;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMode;

/**
 * AI 일정 생성 요청 옵션. 바디 없이 호출하는 기존 클라이언트를 깨지 않기 위해 모든 필드는 선택값이며,
 * 누락 시 기존 동작({@link GenerateMode#STRICT}, 시작 지점 없음, {@link TravelMode#CAR} 단일안)과
 * 동일하게 정규화한다.
 *
 * @param startContentId 여행을 시작할 바구니 항목의 contentId. 일차 배분과 하루 동선 최적화는
 *                       지정 여부와 무관하게 수행하며, 지정하면 이 장소를 첫 스톱으로 고정한다.
 *                       미지정이면 최적화 결과의 첫 장소에서 시작한다.
 * @param travelModes    만들 일정안의 이동수단 목록. 모드마다 일정안이 하나씩 나온다.
 *                       미지정·빈 값이면 자동차 단일안이다.
 */
public record GenerateItineraryRequest(
        GenerateMode mode,
        String startContentId,
        List<TravelMode> travelModes
) {

    /**
     * 한 번에 만들 일정안 수 상한. 안(案) 하나마다 전체 스케줄링을 다시 돌리고 응답에도 일정 전체가
     * 한 벌씩 더 붙어서, 응답 시간과 크기가 안 수에 비례해 늘어난다.
     */
    public static final int MAX_VARIANTS = 4;

    public GenerateItineraryRequest {
        // 서비스가 매번 null 을 방어하지 않도록 진입 시점에 한 번만 정규화한다.
        mode = mode == null ? GenerateMode.STRICT : mode;
        // 빈 문자열은 "미지정"과 같은 의미다. 여기서 null 로 모아두면 뒤에서 isBlank 검사를 반복하지 않는다.
        startContentId = (startContentId == null || startContentId.isBlank()) ? null : startContentId.trim();
        travelModes = normalizeModes(travelModes);
    }

    /** 중복은 같은 일정안을 두 번 만들 뿐이므로 순서를 유지한 채 제거하고, 상한을 넘으면 잘라낸다. */
    private static List<TravelMode> normalizeModes(List<TravelMode> requested) {
        if (requested == null) {
            return List.of(TravelMode.CAR);
        }
        List<TravelMode> normalized = requested.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(MAX_VARIANTS)
                .toList();
        // 빈 리스트나 null 만 담긴 리스트도 "미지정"과 같이 다뤄 기존 동작으로 되돌린다.
        return normalized.isEmpty() ? List.of(TravelMode.CAR) : normalized;
    }

    /** 이동수단 지정 없이 모드·시작 지점만 지정하는 호출. 필드가 늘어나도 기존 호출부가 그대로 남도록 둔다. */
    public GenerateItineraryRequest(GenerateMode mode, String startContentId) {
        this(mode, startContentId, null);
    }

    /** 시작 지점 없이 모드만 지정하는 호출. 필드가 늘어나도 기존 호출부가 그대로 남도록 둔다. */
    public GenerateItineraryRequest(GenerateMode mode) {
        this(mode, null, null);
    }

    /** 바디 없이 호출된 경우 사용할 기본 요청. 필드가 늘어나도 호출부가 그대로 남도록 팩터리로 둔다. */
    public static GenerateItineraryRequest defaults() {
        return new GenerateItineraryRequest(null, null, null);
    }
}
