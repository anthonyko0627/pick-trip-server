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
 */
public record AiItineraryRequest(
        String regionName,
        LocalDate travelDate,
        Integer duration,
        List<String> companions,
        List<AiPlace> places
) {
}
