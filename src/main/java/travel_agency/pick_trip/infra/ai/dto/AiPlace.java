package travel_agency.pick_trip.infra.ai.dto;

/**
 * AI 일정 생성에 전달하는 개별 장소 입력.
 * 바구니 스냅샷(contentId·title·priority)에 더해, 가능하면 콘텐츠 상세
 * (좌표·운영시간·휴무일·체류시간)를 보강해 제약 기반 동선 추론에 활용한다.
 * 상세 조회 실패 시 좌표·운영시간 등은 null 일 수 있다.
 *
 * <p>{@code priority} 는 enum 코드가 아니라 한국어 라벨이다 ({@code Priority.getLabel()}).
 */
public record AiPlace(
        String contentId,
        String title,
        String category,
        Double latitude,
        Double longitude,
        String useTime,
        String restDate,
        String stayDuration,
        String priority
) {
}
