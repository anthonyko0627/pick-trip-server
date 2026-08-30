package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * TourAPI {@code /locationBasedList2}(좌표·반경 기반 목록) 응답. 표준 목록 응답과 동일한 구조에
 * 항목별 기준 좌표로부터의 거리 {@code dist}(m)가 추가된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiLocationListResponse(Response response) implements TourApiResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
        /** 헤더 없는 과거 호환 생성자 (테스트·내부 생성용). */
        public Response(Body body) {
            this(null, body);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String title,
            String addr1,
            String addr2,
            String mapx,
            String mapy,
            String firstimage,
            /** 기준 좌표로부터의 거리(m). */
            String dist,
            String areacode,
            String sigungucode,
            String lclsSystm1,
            String lclsSystm2
    ) {}

    @Override
    public String resultCode() {
        return response != null && response.header() != null ? response.header().resultCode() : null;
    }

    @Override
    public String resultMsg() {
        return response != null && response.header() != null ? response.header().resultMsg() : null;
    }
}
