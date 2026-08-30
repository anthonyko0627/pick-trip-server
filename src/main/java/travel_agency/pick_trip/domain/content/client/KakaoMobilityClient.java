package travel_agency.pick_trip.domain.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestRequest;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestResponse;

/**
 * Kakao Mobility 길찾기. 근처 콘텐츠를 실제 도로 거리로 정렬하기 위해
 * 기준 좌표 1점 → 주변 후보 N점(최대 30)의 도로 거리·소요 시간을 1콜로 조회한다.
 */
@FeignClient(
        name = "kakao-mobility",
        url = "${kakao-mobility.base-url}",
        configuration = KakaoMobilityFeignConfig.class
)
public interface KakaoMobilityClient {

    @PostMapping("/v1/destinations/directions")
    KakaoMultiDestResponse getMultiDestinationDirections(@RequestBody KakaoMultiDestRequest request);
}
