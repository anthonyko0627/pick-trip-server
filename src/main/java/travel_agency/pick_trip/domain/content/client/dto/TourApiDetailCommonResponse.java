package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiDetailCommonResponse(Response response) implements TourApiResponse {

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
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String title,
            String addr1,
            String addr2,
            String tel,
            String homepage,
            String mapx,
            String mapy,
            String firstimage,
            String overview,
            String lclsSystm1,
            String lclsSystm2,
            String lclsSystm3,
            String lDongRegnCd,
            String lDongSignguCd
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
