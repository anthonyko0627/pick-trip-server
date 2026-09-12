package travel_agency.pick_trip.domain.content.dto.response;

import java.time.LocalDate;

/**
 * 콘텐츠 응답에 붙는 관광객수 지표.
 *
 * <p><b>{@code approximate} 는 항상 {@code true} 다.</b> 개별 장소 단위 관광객수를 주는 공개 데이터가
 * 없어, 지역(시군구) 단위 방문자수 통계를 그 지역 콘텐츠의 값으로 그대로 내려주거나
 * (통계가 없으면) 바구니에 담긴 횟수라는 자체 프록시로 대체하기 때문이다. 어느 쪽도 그 장소의
 * 실제 방문자수가 아니므로 클라이언트는 반드시 근사값으로 표시해야 한다 (#73).
 *
 * <p>지역 통계도 자체 프록시도 없으면 이 객체 자체가 {@code null} 로 응답된다.
 *
 * @param totalVisitors        기간 누적 방문자수 (프록시일 때는 바구니에 담긴 횟수)
 * @param dailyAverageVisitors 일평균 방문자수 (프록시일 때는 산출 근거가 없어 null)
 * @param period               집계 기간 (예: "2026-01~2026-06"). 프록시일 때는 null
 * @param source               출처 표기
 * @param baseDate             기준일
 * @param approximate          근사 여부 (항상 true)
 */
public record VisitorStatsResponse(
        Long totalVisitors,
        Long dailyAverageVisitors,
        String period,
        String source,
        LocalDate baseDate,
        boolean approximate
) {
}
