package travel_agency.pick_trip.domain.content.client.dto;

import java.util.List;

/**
 * Kakao Mobility 여러 목적지 길찾기 요청 본문.
 * 좌표는 {@code x}=경도(longitude), {@code y}=위도(latitude)이며, 목적지는 최대 30개.
 * {@code radius}(m)는 필수이며 최대 10,000. 기준점에서 이 반경(직선) 밖 목적지는 결과에서 빠진다.
 */
public record KakaoMultiDestRequest(
        Point origin,
        List<Destination> destinations,
        int radius
) {

    public record Point(double x, double y) {}

    /** {@code key}는 응답 {@code routes[].key}로 되돌아오므로 콘텐츠 식별자를 넣는다. */
    public record Destination(double x, double y, String key) {}
}
