package travel_agency.pick_trip.domain.itinerary.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * OpenTopoData 고도 조회 응답.
 *
 * <p>{@code results}는 요청한 좌표 순서를 그대로 유지하므로 인덱스로 좌표와 짝지운다.
 * 데이터셋 범위 밖 좌표는 {@code elevation}이 {@code null}로 내려오며, 그 지점은 고도 미상으로 둔다.
 * {@code status}는 성공 시 {@code "OK"}이고 실패는 4xx/5xx 로 오므로 별도 분기 없이 에러로 흐른다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ElevationResponse(String status, List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            /** 해발고도(m). 데이터가 없는 좌표는 null. */
            Double elevation
    ) {}
}
