package travel_agency.pick_trip.domain.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.content.client.dto.RegionVisitorResponse;

/**
 * 공공데이터포털 "한국관광공사 빅데이터 지역별 방문자수"(15101972, DataLabService) 클라이언트.
 * 인증키는 TourAPI 와 같은 {@code PUBLIC_DATA_PORTAL_KEY} 를 재사용한다.
 *
 * <p>제공 단위가 광역/기초지자체라 개별 장소 단위 관광객수는 얻을 수 없다. 콘텐츠 응답에는
 * 이 지역 통계를 근사값으로 내려주고, 통계가 없으면 자체 프록시로 폴백한다 (#73).
 */
@FeignClient(
        name = "visitor-stats",
        url = "${visitor-stats.base-url}",
        configuration = VisitorStatsFeignConfig.class
)
public interface VisitorStatsClient {

    /**
     * 기초지자체(시군구) 일자별 방문자수. 매뉴얼(관광빅데이터 v4.1)상 지역 필터 파라미터가 없어
     * 기간 안의 <b>전국 모든 시군구</b>가 내려온다(하루 ≈ 시군구 수 × 관광객 구분 3종 ≈ 740행).
     * 호출 측이 {@code signguCode} 로 원하는 지역을 걸러야 한다 (#85).
     */
    @GetMapping("/locgoRegnVisitrDDList")
    RegionVisitorResponse getLocalRegionVisitors(
            @RequestParam String startYmd,
            @RequestParam String endYmd,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );
}
