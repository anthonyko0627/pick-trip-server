package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TourApiDetailCommonResponse")
class TourApiDetailCommonResponseTest {

    /** {@code TourApiFeignConfig#tourApiDecoder} 와 동일한 설정. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    @Test
    @DisplayName("detailCommon2 응답의 법정동 코드를 역직렬화한다")
    void deserializesLdongCodes() throws Exception {
        // given - 실제 /detailCommon2?contentId=127669(부석사) 응답에서 발췌. areacode는 빈 문자열로 내려온다.
        String json = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"numOfRows":1,"pageNo":1,"totalCount":1,"items":{"item":[
                {"contentid":"127669","contenttypeid":"12","title":"부석사",
                 "areacode":"","sigungucode":"","lDongRegnCd":"47","lDongSignguCd":"210"}
                ]}}}}
                """;

        // when
        TourApiDetailCommonResponse response =
                objectMapper.readValue(json, TourApiDetailCommonResponse.class);

        // then
        TourApiDetailCommonResponse.Item item =
                response.response().body().items().item().get(0);
        assertThat(item.contentid()).isEqualTo("127669");
        assertThat(item.lDongRegnCd()).isEqualTo("47");
        assertThat(item.lDongSignguCd()).isEqualTo("210");
    }
}
