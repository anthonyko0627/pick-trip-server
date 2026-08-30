package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Kakao Mobility 여러 목적지 길찾기 응답.
 * 목적지별로 {@code result_code == 0} 이면 성공이며 {@code summary}에 거리(m)·시간(s)이 담긴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoMultiDestResponse(List<Route> routes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(
            String key,
            @JsonProperty("result_code") Integer resultCode,
            Summary summary
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            /** 도로 거리(m). */
            Integer distance,
            /** 예상 소요 시간(s). */
            Integer duration
    ) {}
}
