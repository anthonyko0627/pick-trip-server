package travel_agency.pick_trip.domain.itinerary.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.itinerary.client.dto.ElevationResponse;

/**
 * OpenTopoData 고도 조회. 도보 leg 의 상승고도를 계산하기 위해 하루치 좌표를 1콜로 조회한다.
 *
 * <p>데이터 소스 선정 근거와 후보 비교는 {@code .agents/docs/elevation-source.md} 참고.
 * 인증키가 없는 공개 엔드포인트라 별도 RequestInterceptor 를 두지 않는다.
 *
 * <p>데이터셋은 {@code srtm30m} 으로 고정한다. 전 지구 30m 격자로 하동·영주·예천(북위 33~38도)을
 * 모두 덮으며, 관광 도보 구간의 오르막 판단에는 이 해상도로 충분하다. 자체 호스팅으로 옮기더라도
 * 같은 데이터셋 이름을 쓰므로 {@code elevation.base-url} 만 바꾸면 된다.
 */
@FeignClient(
        name = "elevation",
        url = "${elevation.base-url}",
        configuration = ElevationFeignConfig.class
)
public interface ElevationClient {

    /**
     * @param locations {@code "위도,경도|위도,경도"} 형식. 결과는 요청 순서를 그대로 유지한다.
     */
    @GetMapping("/v1/srtm30m")
    ElevationResponse getElevations(@RequestParam("locations") String locations);
}
