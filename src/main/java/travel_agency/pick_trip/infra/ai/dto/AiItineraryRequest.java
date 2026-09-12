package travel_agency.pick_trip.infra.ai.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 일정 생성 요청 입력 모델.
 * 여행 조건(지역·날짜·기간·동행)과 장소 목록을 담아 {@code AiItineraryClient}에 전달한다.
 *
 * <p>{@code companions} 는 enum 코드가 아니라 한국어 라벨 목록이다
 * ({@code TravelCondition.getLabel()}). enum name 이 프롬프트를 거쳐 사용자 화면(reason)에
 * 새지 않도록 도메인 계층에서 미리 변환해 전달한다.
 *
 * <p>{@code extraCandidates} 는 AUGMENT 모드에서 AI 가 골라 쓸 수 있는 같은 지역의 추가 후보다
 * (id·이름·분류만 채운 {@link AiPlace}). 비어 있으면 추가 제안을 금지하는 STRICT 프롬프트가 나가므로,
 * 모드 플래그를 따로 두지 않고 이 목록 하나로 프롬프트 분기를 결정한다.
 * 실제 허용 범위는 서버 화이트리스트가 최종 결정한다.
 */
public record AiItineraryRequest(
        String regionName,
        LocalDate travelDate,
        Integer duration,
        List<String> companions,
        List<AiPlace> places,
        List<AiPlace> extraCandidates
) {

    public AiItineraryRequest {
        // 프롬프트 조립부가 매번 null 을 방어하지 않도록 여기서 한 번만 정규화한다.
        extraCandidates = extraCandidates == null ? List.of() : extraCandidates;
    }
}
