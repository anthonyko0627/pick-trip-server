package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 공공데이터포털 "한국관광공사 빅데이터 지역별 방문자수"(15101972) 의
 * 기초지자체 일자별 방문자수({@code /locgoRegnVisitrDDList}) 응답.
 * 필드명은 관광빅데이터 활용매뉴얼 v4.1 §3-3 기준이다.
 *
 * <p>TourAPI 와 같은 {@code response.header/body} 구조라 {@link TourApiResponse} 계약을 그대로 쓴다.
 * {@code touNum}(관광객수)은 원천이 소수로 내려준다(예: {@code 176473.5}) 므로 {@link Double} 로 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegionVisitorResponse(Response response) implements TourApiResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            /** 시군구 코드 5자리(법정동 시도 2자리 + 시군구 3자리). 예: 영주시 {@code 47210}. */
            String signguCode,
            String signguNm,
            /** 요일 구분 코드. 1=월 … 7=일. */
            String daywkDivCd,
            String daywkDivNm,
            /** 관광객 구분 코드. 1=현지인, 2=외지인, 3=외국인. */
            String touDivCd,
            String touDivNm,
            Double touNum,
            String baseYmd
    ) {}

    /** 응답이 비어 있어도 안전하게 빈 목록을 반환한다. */
    public List<Item> items() {
        if (response == null
                || response.body() == null
                || response.body().items() == null
                || response.body().items().item() == null) {
            return List.of();
        }
        return response.body().items().item();
    }

    /** 전체 결과 수. 본문이 없으면 0. */
    public int totalCount() {
        return response == null || response.body() == null ? 0 : response.body().totalCount();
    }

    @Override
    public String resultCode() {
        return response != null && response.header() != null ? response.header().resultCode() : null;
    }

    @Override
    public String resultMsg() {
        return response != null && response.header() != null ? response.header().resultMsg() : null;
    }
}
